package com.be.assignment;

import com.be.course.entity.Course;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.enrollment.dto.*;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.*;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.enrollment.service.EnrollmentService;
import com.be.global.exception.*;
import com.be.member.enums.MemberStatus;
import com.be.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import static com.be.assignment.AssignmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 수동 배정 상태·출처·마감일과 관리/본인 조회 범위 검증
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {
    @Mock EnrollmentRepository enrollments;
    @Mock CourseRepository courses;
    @Mock MemberRepository members;
    EnrollmentService service;

    @BeforeEach
    void setUp() { service = new EnrollmentService(enrollments, courses, members, CLOCK, mock(com.be.notification.service.NotificationService.class)); }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"ACTIVE", "ON_LEAVE"})
    void manualAllowsActiveAndLeaveAndSnapshotsDeadline(MemberStatus status) {
        var course = open();
        var member = member(2L);
        if (status == MemberStatus.ON_LEAVE) member.changeStatus(status, LocalDateTime.now(CLOCK));
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));
        when(enrollments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.assignManually(1L, new ManualEnrollmentRequest(2L));
        assertThat(result.status()).isEqualTo(EnrollmentStatus.ASSIGNED);
        assertThat(result.assignmentSource()).isEqualTo(AssignmentSource.MANUAL);
        assertThat(result.assignmentRuleId()).isNull();
        assertThat(result.courseType()).isEqualTo(com.be.course.enums.CourseType.MANDATORY);
        assertThat(result.courseStartDate()).isEqualTo(course.getStartDate());
        assertThat(result.courseEndDate()).isEqualTo(course.getEndDate());
        assertThat(result.dueDate()).isEqualTo(course.getEndDate());
        assertThat(result.startedAt()).isNull();
        assertThat(result.completedAt()).isNull();
        var capture = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollments).saveAndFlush(capture.capture());
        var originalDueDate = course.getEndDate();
        course.update(course.getTitle(), null, course.getCourseType(), course.getStartDate(), originalDueDate.plusDays(5),
                course.getPassingProgressRate(), null);
        assertThat(capture.getValue().getDueDate()).isEqualTo(originalDueDate);
    }

    @Test
    void rejectsResignedAndDuplicateEnrollment() {
        var course = open();
        var member = member(2L);
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));
        when(enrollments.findExistingForAssignment(2L, 1L)).thenReturn(Optional.of(Enrollment.manual(member, course, LocalDateTime.now(CLOCK))));
        assertCode(ErrorCode.DUPLICATE_ENROLLMENT, () -> service.assignManually(1L, new ManualEnrollmentRequest(2L)));
        member.changeStatus(MemberStatus.RESIGNED, LocalDateTime.now(CLOCK));
        assertCode(ErrorCode.RESIGNED_MEMBER_ASSIGNMENT, () -> service.assignManually(1L, new ManualEnrollmentRequest(2L)));
        verify(enrollments, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = CourseStatus.class, names = {"DRAFT", "CLOSED"})
    void manualRequiresOpenCourse(CourseStatus status) {
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course(status)));
        assertCode(ErrorCode.COURSE_NOT_OPEN_FOR_ASSIGNMENT,
                () -> service.assignManually(1L, new ManualEnrollmentRequest(2L)));
        verifyNoInteractions(members, enrollments);
    }

    @Test
    void rejectsMissingResources() {
        assertCode(ErrorCode.COURSE_NOT_FOUND, () -> service.assignManually(99L, new ManualEnrollmentRequest(2L)));
        open();
        assertCode(ErrorCode.MEMBER_NOT_FOUND, () -> service.assignManually(1L, new ManualEnrollmentRequest(99L)));
        assertCode(ErrorCode.ENROLLMENT_NOT_FOUND, () -> service.get(99L));
        assertCode(ErrorCode.COURSE_NOT_FOUND, () -> service.forCourse(99L, new EnrollmentSearchRequest(null, null, null)));
    }

    @Test
    void scopesSearchByMemberOrCourseAndUsesPageDefaults() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("id"));
        when(enrollments.search(null, 2L, EnrollmentStatus.ASSIGNED, pageable)).thenReturn(Page.empty(pageable));
        assertThat(service.forMember(2L, new EnrollmentSearchRequest(null, null, EnrollmentStatus.ASSIGNED)).getSize()).isEqualTo(20);
        when(courses.existsById(1L)).thenReturn(true);
        when(enrollments.search(1L, null, null, pageable)).thenReturn(Page.empty(pageable));
        assertThat(service.forCourse(1L, new EnrollmentSearchRequest(null, null, null))).isEmpty();
        verify(enrollments).search(null, 2L, EnrollmentStatus.ASSIGNED, pageable);
        verify(enrollments).search(1L, null, null, pageable);
    }

    @Test
    void readsManualDetailWithNullRule() {
        var value = Enrollment.manual(member(2L), course(CourseStatus.OPEN), LocalDateTime.now(CLOCK));
        id(value, 50L);
        when(enrollments.findById(50L)).thenReturn(Optional.of(value));
        assertThat(service.get(50L).enrollmentId()).isEqualTo(50L);
        assertThat(service.get(50L).assignmentRuleId()).isNull();
    }

    private Course open() {
        var course = course(CourseStatus.OPEN);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        return course;
    }

    @Test
    void responseUsesManagedCopyReturnedByJpaMerge() {
        var course = open();
        var member = member(2L);
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));
        var managed = Enrollment.manual(member, course, LocalDateTime.now(CLOCK));
        id(managed, 50L);
        when(enrollments.saveAndFlush(any())).thenReturn(managed);
        assertThat(service.assignManually(1L, new ManualEnrollmentRequest(2L)).enrollmentId()).isEqualTo(50L);
    }
    private void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}

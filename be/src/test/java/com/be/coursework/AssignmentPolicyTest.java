package com.be.coursework;
import com.be.assignment.enums.AssignmentRuleType;
import com.be.assignment.repository.AssignmentRuleRepository;
import com.be.assignment.service.AutoAssignmentService;
import com.be.course.dto.*;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.course.service.CourseService;
import com.be.coursework.repository.AssignmentRepository;
import com.be.coursework.service.AssignmentPolicy;
import com.be.enrollment.dto.ManualEnrollmentRequest;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.enrollment.service.EnrollmentService;
import com.be.global.exception.*;
import com.be.member.repository.MemberRepository;
import com.be.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.be.coursework.CourseworkFixtures.*;
import static com.be.coursework.AssignmentServiceTest.error;
import static com.be.assignment.AssignmentFixtures.member;
import static com.be.assignment.AssignmentFixtures.rule;
import static com.be.assignment.AssignmentFixtures.id;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentPolicyTest {
    final AssignmentRepository assignments = mock(AssignmentRepository.class);
    final AssignmentPolicy policy = new AssignmentPolicy(assignments);
    final CourseRepository courses = mock(CourseRepository.class);
    final MemberRepository members = mock(MemberRepository.class);
    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final AssignmentRuleRepository rules = mock(AssignmentRuleRepository.class);
    final NotificationService notifications = mock(NotificationService.class);
    @Test void manualRejectsLateRequiredAssignment() {
        var course = course(CourseStatus.OPEN);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member(2)));
        when(assignments.existsByCourseIdAndIsRequiredTrueAndDueDateBefore(1L,DUE)).thenReturn(true);
        var service = new EnrollmentService(enrollments,courses,members,CLOCK,notifications,policy);
        error(() -> service.assignManually(1L,new ManualEnrollmentRequest(2L)),ErrorCode.ASSIGNMENT_ENROLLMENT_DEADLINE_PASSED);
        verify(enrollments,never()).saveAndFlush(any()); verifyNoInteractions(notifications);
    }
    @Test void assignmentDueDateTodayIsNotPastAndNoAssignmentsPreservesExistingPolicy() {
        assertThat(policy.blocksEnrollment(course(CourseStatus.OPEN),DUE)).isFalse();
        verify(assignments).existsByCourseIdAndIsRequiredTrueAndDueDateBefore(1L,DUE);
        var course = course(CourseStatus.OPEN);
        assertThat(policy.blocksEnrollment(course,DUE.plusDays(1))).isFalse();
        when(assignments.existsByCourseIdAndIsRequiredTrue(1L)).thenReturn(true);
        assertThat(policy.blocksEnrollment(course,DUE.plusDays(1))).isTrue();
    }
    @Test void automaticMemberTriggerSkipsBlockedCourseAndContinuesOthers() {
        var blocked = course(CourseStatus.OPEN); var available = course(CourseStatus.OPEN); id(available,2L);
        when(courses.findOpenForAssignment()).thenReturn(List.of(blocked,available));
        when(assignments.existsByCourseIdAndIsRequiredTrueAndDueDateBefore(1L,DUE)).thenReturn(true);
        when(rules.findActiveForAssignment(2L)).thenReturn(List.of(rule(available,AssignmentRuleType.ALL_EMPLOYEES)));
        when(enrollments.saveAndFlush(any())).thenAnswer(c -> c.getArgument(0));
        auto().assignMember(member(2));
        verify(rules,never()).findActiveForAssignment(1L);
        verify(enrollments).findExistingForAssignment(2L,2L);
        verify(enrollments,times(1)).saveAndFlush(any());
    }
    @Test void automaticCourseAndRuleTriggersSkipExpiredAssignmentWithoutThrowing() {
        var course = course(CourseStatus.OPEN); var rule = rule(course,AssignmentRuleType.ALL_EMPLOYEES);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(rules.findActiveForAssignment(1L)).thenReturn(List.of(rule));
        when(rules.findForUpdate(1L,100L)).thenReturn(Optional.of(rule));
        when(assignments.existsByCourseIdAndIsRequiredTrueAndDueDateBefore(1L,DUE)).thenReturn(true);
        auto().assignCourse(1L); auto().assignRule(1L,100L);
        verifyNoInteractions(enrollments,members,notifications);
    }
    @Test void openingAndChangingCoursePeriodRevalidateAssignmentDates() {
        var course = course(CourseStatus.DRAFT);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(assignments.hasOutsidePeriod(1L,course.getStartDate(),DUE)).thenReturn(true);
        var auto = mock(AutoAssignmentService.class);
        var service = new CourseService(courses,members,auto,policy);
        error(() -> service.changeStatus(1L,new CourseStatusRequest(CourseStatus.OPEN)),ErrorCode.INVALID_ASSIGNMENT_DUE_DATE);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.DRAFT);
        var patch = new CoursePatchRequest(); patch.setEndDate(DUE);
        error(() -> service.update(1L,patch),ErrorCode.INVALID_ASSIGNMENT_DUE_DATE);
        verifyNoInteractions(auto);
    }
    AutoAssignmentService auto() { return new AutoAssignmentService(courses,rules,members,enrollments,CLOCK,mock(EntityManager.class),notifications,policy); }
}

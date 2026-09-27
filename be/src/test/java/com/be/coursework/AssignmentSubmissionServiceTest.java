package com.be.coursework;
import com.be.course.entity.Course;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.coursework.dto.*;
import com.be.coursework.entity.*;
import com.be.coursework.enums.*;
import com.be.coursework.repository.*;
import com.be.coursework.service.*;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.enrollment.service.EnrollmentCompletionService;
import com.be.global.exception.*;
import com.be.member.enums.Role;
import com.be.member.repository.MemberRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import static com.be.coursework.CourseworkFixtures.*;
import static com.be.coursework.AssignmentServiceTest.error;
import static com.be.assignment.AssignmentFixtures.member;
import static com.be.assignment.AssignmentFixtures.id;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentSubmissionServiceTest {
    final AssignmentRepository assignments = mock(AssignmentRepository.class);
    final AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final CourseRepository courses = mock(CourseRepository.class);
    final MemberRepository members = mock(MemberRepository.class);
    final EnrollmentCompletionService completion = mock(EnrollmentCompletionService.class);
    final Course course = course(CourseStatus.OPEN);
    final Enrollment enrollment = enrollment(course);
    final Assignment assignment = assignment(course, 20, true);
    AssignmentSubmission saved;
    AssignmentSubmissionService service;
    @BeforeEach void setup() {
        service = service(CLOCK);
        when(enrollments.findForCourseworkUpdate(10L)).thenReturn(Optional.of(enrollment));
        when(enrollments.findById(10L)).thenReturn(Optional.of(enrollment));
        when(assignments.findByIdAndCourseId(20L,1L)).thenReturn(Optional.of(assignment));
        when(assignments.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(assignment));
        when(submissions.findForUpdate(20L,10L)).thenAnswer(c -> Optional.ofNullable(saved));
        when(submissions.findForGrading(30L)).thenAnswer(c -> Optional.ofNullable(saved));
        when(submissions.findById(30L)).thenAnswer(c -> Optional.ofNullable(saved));
        when(submissions.findEnrollmentId(30L)).thenReturn(Optional.of(10L));
        when(submissions.findByEnrollmentId(10L)).thenAnswer(c -> saved == null ? List.of() : List.of(saved));
        when(submissions.saveAndFlush(any())).thenAnswer(c -> { saved = c.getArgument(0); id(saved,30L); return saved; });
        when(members.findById(3L)).thenReturn(Optional.of(member(3)));
        when(members.findById(4L)).thenReturn(Optional.of(member(4)));
    }
    AssignmentSubmissionService service(Clock clock) {
        return new AssignmentSubmissionService(assignments,submissions,enrollments,courses,members,new CourseworkAccess(),completion,clock);
    }
    @Test void firstSubmissionStartsLearningAndPendingResubmissionReusesRow() {
        assertThat(service.mine(10L,OWNER).getFirst().status()).isEqualTo(AssignmentSubmissionStatus.NOT_SUBMITTED);
        var first = service.submit(10L,20L,OWNER,text(" first "));
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        assertThat(first.submission().content()).isEqualTo("first");
        assertThat(first.submission().submissionCount()).isEqualTo(1);
        var original = saved;
        service.submit(10L,20L,OWNER,text("second"));
        assertThat(saved).isSameAs(original);
        assertThat(saved.getSubmissionCount()).isEqualTo(2);
        assertThat(saved.getScore()).isNull();
        assertThat(enrollment.getStartedAt()).isEqualTo(NOW);
        verifyNoInteractions(completion);
    }
    @Test void failedGradeKeepsEnrollmentInProgressAndResubmissionClearsEveryGradeField() {
        service.submit(10L,20L,OWNER,text("one"));
        service.grade(30L,INSTRUCTOR,new GradeRequest(new BigDecimal("79.99"),"다시"));
        assertThat(saved.getPassed()).isFalse();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        verifyNoInteractions(completion);
        var later = service(Clock.offset(CLOCK,Duration.ofMillis(1)));
        later.submit(10L,20L,OWNER,new SubmissionRequest(AssignmentSubmissionType.URL,"https://example.com/work"));
        assertThat(saved.getSubmissionCount()).isEqualTo(2);
        assertThat(saved.getSubmittedAt()).isAfter(NOW);
        assertThat(saved.getScore()).isNull(); assertThat(saved.getPassed()).isNull();
        assertThat(saved.getFeedback()).isNull(); assertThat(saved.getGradedAt()).isNull(); assertThat(saved.getGradedBy()).isNull();
    }
    @Test void exactPassingScoreCallsSharedCompletionAndCannotRegradeOrResubmit() {
        service.submit(10L,20L,OWNER,text("one"));
        var response = service.grade(30L,ADMIN,new GradeRequest(new BigDecimal("80"),"통과"));
        assertThat(response.passed()).isTrue(); assertThat(response.gradedByMemberId()).isEqualTo(3);
        verify(completion).evaluate(enrollment,NOW);
        error(() -> service.grade(30L,ADMIN,new GradeRequest(BigDecimal.ZERO,null)),ErrorCode.ASSIGNMENT_ALREADY_GRADED);
        error(() -> service.submit(10L,20L,OWNER,text("two")),ErrorCode.ASSIGNMENT_ALREADY_PASSED);
    }
    @ParameterizedTest @CsvSource({"TEXT,hello","URL,https://example.com/work","URL,http://example.com"})
    void validTextAndUrls(AssignmentSubmissionType type,String value) {
        assertThat(service.submit(10L,20L,OWNER,new SubmissionRequest(type,value)).submission().content()).isEqualTo(value);
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "/relative", "ftp://example.com", "https:opaque", "https:///missing-host", "javascript:alert(1)", "https://bad host"})
    void invalidUrlsRejected(String value) {
        error(() -> service.submit(10L,20L,OWNER,new SubmissionRequest(AssignmentSubmissionType.URL,value)),ErrorCode.INVALID_ASSIGNMENT_CONTENT);
        verify(submissions,never()).saveAndFlush(any());
    }
    @Test void blankAndOversizedContentRejected() {
        for(String text : List.of("  ","a".repeat(10001)))
            error(() -> service.submit(10L,20L,OWNER,text(text)),ErrorCode.INVALID_ASSIGNMENT_CONTENT);
        error(() -> service.submit(10L,20L,OWNER,new SubmissionRequest(AssignmentSubmissionType.URL,"https://example.com/"+"a".repeat(2048))),
            ErrorCode.INVALID_ASSIGNMENT_CONTENT);
    }
    @Test void seoulDueDateInclusiveThenMidnightBlocksNewAndRepeatedSubmissions() {
        service.submit(10L,20L,OWNER,text("on time"));
        var tomorrow = service(Clock.offset(CLOCK,Duration.ofSeconds(1)));
        error(() -> tomorrow.submit(10L,20L,OWNER,text("too late")),ErrorCode.ASSIGNMENT_DEADLINE_PASSED);
        saved = null;
        error(() -> tomorrow.submit(10L,20L,OWNER,text("new late")),ErrorCode.ASSIGNMENT_DEADLINE_PASSED);
        assertThat(tomorrow.mine(10L,OWNER).getFirst().submittable()).isFalse();
    }
    @Test void earlierEnrollmentDeadlineWins() {
        ReflectionTestUtils.setField(enrollment,"dueDate",DUE.minusDays(1));
        error(() -> service.submit(10L,20L,OWNER,text("late")),ErrorCode.ASSIGNMENT_DEADLINE_PASSED);
        assertThat(service.mine(10L,OWNER).getFirst().effectiveDueDate()).isEqualTo(DUE.minusDays(1));
    }
    @Test void earlierAssignmentDeadlineWins() {
        ReflectionTestUtils.setField(assignment,"dueDate",DUE.minusDays(1));
        error(() -> service.submit(10L,20L,OWNER,text("late")),ErrorCode.ASSIGNMENT_DEADLINE_PASSED);
    }
    @Test void gradingRemainsAllowedAfterDeadlineAndInClosedCourse() {
        service.submit(10L,20L,OWNER,text("on time"));
        course.changeStatus(CourseStatus.CLOSED);
        var after = service(Clock.offset(CLOCK,Duration.ofDays(1)));
        assertThat(after.grade(30L,INSTRUCTOR,new GradeRequest(BigDecimal.valueOf(90),null)).passed()).isTrue();
    }
    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = {"COMPLETED","FAILED","EXPIRED"})
    void terminalEnrollmentBlocksWritesButAllowsReads(EnrollmentStatus status) {
        ReflectionTestUtils.setField(enrollment,"status",status);
        error(() -> service.submit(10L,20L,OWNER,text("x")),ErrorCode.ASSIGNMENT_SUBMISSION_NOT_EDITABLE);
        assertThat(service.mine(10L,OWNER).getFirst().submittable()).isFalse();
    }
    @Test void ownerAndInstructorAclCannotBeBypassedBySubmissionId() {
        for(var p : List.of(principal(99,Role.EMPLOYEE),ADMIN,INSTRUCTOR)) {
            error(() -> service.mine(10L,p),ErrorCode.ASSIGNMENT_ACCESS_DENIED);
            error(() -> service.submit(10L,20L,p,text("x")),ErrorCode.ASSIGNMENT_ACCESS_DENIED);
        }
        service.submit(10L,20L,OWNER,text("own"));
        for(var p : List.of(OWNER,principal(99,Role.INSTRUCTOR))) {
            error(() -> service.get(30L,p),ErrorCode.ASSIGNMENT_ACCESS_DENIED);
            error(() -> service.grade(30L,p,new GradeRequest(BigDecimal.TEN,null)),ErrorCode.ASSIGNMENT_ACCESS_DENIED);
        }
        assertThat(service.get(30L,INSTRUCTOR).submissionId()).isEqualTo(30);
    }
    @Test void foreignCourseAssignmentIsNotAccepted() {
        error(() -> service.submit(10L,99L,OWNER,text("x")),ErrorCode.ASSIGNMENT_NOT_FOUND);
    }
    @Test void lockOrderAndOptimisticConflictPropagateWithoutCompletionOrRetry() {
        var failure = new OptimisticLockingFailureException("concurrent");
        doThrow(failure).when(submissions).saveAndFlush(any());
        assertThatThrownBy(() -> service.submit(10L,20L,OWNER,text("x"))).isSameAs(failure);
        var ordered = inOrder(enrollments,submissions);
        ordered.verify(enrollments).findForCourseworkUpdate(10L);
        ordered.verify(submissions).findForUpdate(20L,10L);
        ordered.verify(submissions).saveAndFlush(any());
        verifyNoInteractions(completion);
    }
    @Test void versionAndEnrollmentForceIncrementAreMapped() throws Exception {
        assertThat(AssignmentSubmission.class.getDeclaredField("version").isAnnotationPresent(jakarta.persistence.Version.class)).isTrue();
        assertThat(EnrollmentRepository.class.getMethod("findForCourseworkUpdate",Long.class)
            .getAnnotation(org.springframework.data.jpa.repository.Lock.class).value())
            .isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }
    private SubmissionRequest text(String value) { return new SubmissionRequest(AssignmentSubmissionType.TEXT,value); }
}

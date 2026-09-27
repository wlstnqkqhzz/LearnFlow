package com.be.coursework;

import com.be.course.entity.*;
import com.be.course.enums.*;
import com.be.course.repository.*;
import com.be.coursework.entity.*;
import com.be.coursework.repository.AssignmentSubmissionRepository;
import com.be.enrollment.entity.*;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.*;
import com.be.enrollment.service.*;
import com.be.exam.repository.*;
import com.be.notification.enums.NotificationType;
import com.be.notification.service.NotificationService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static com.be.coursework.CourseworkFixtures.*;
import static com.be.assignment.AssignmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentCompletionTest {
    final ExamRepository exams = mock(ExamRepository.class);
    final ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
    final CourseContentRepository contents = mock(CourseContentRepository.class);
    final ContentProgressRepository progresses = mock(ContentProgressRepository.class);
    final AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
    final NotificationService notifications = mock(NotificationService.class);
    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final EnrollmentCompletionService completion = new EnrollmentCompletionService(exams, attempts, contents, progresses, notifications, submissions);
    final EnrollmentExpirationProcessor expiration = new EnrollmentExpirationProcessor(enrollments,notifications,completion);
    final Course course = CourseworkFixtures.course(CourseStatus.OPEN);
    final Enrollment enrollment = CourseworkFixtures.enrollment(course);

    @BeforeEach void setup() {
        enrollment.startLearning(CourseworkFixtures.NOW.minusDays(1));
        when(enrollments.findById(10L)).thenReturn(Optional.of(enrollment));
    }
    @ParameterizedTest @ValueSource(strings = {"NOT_SUBMITTED","PENDING_GRADING","FAILED"})
    void anyUnsatisfiedRequiredPreventsCompletion(String state) {
        when(submissions.hasUnsatisfiedRequired(1L,10L)).thenReturn(true);
        completion.evaluate(enrollment,CourseworkFixtures.NOW);
        assertThat(enrollment.getStatus()).as(state).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        verifyNoInteractions(notifications);
    }
    @Test void noContentsNoExamAndAllRequiredPassedCompletesOnlyOnce() {
        completion.evaluate(enrollment,CourseworkFixtures.NOW);
        completion.evaluate(enrollment,CourseworkFixtures.NOW.plusHours(1));
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getCompletedAt()).isEqualTo(CourseworkFixtures.NOW);
        verify(notifications,times(1)).notify(enrollment,NotificationType.COURSE_COMPLETED);
    }
    @ParameterizedTest @ValueSource(strings = {"CTA","CAT","TCA","TAC","ACT","ATC"})
    void allSixCompletionOrdersUseSameEvaluator(String order) {
        var content = CourseContent.create(course,"필수",ContentType.LINK,"https://example.com",null,1,true);
        id(content,50L);
        var progress = ContentProgress.create(enrollment,content);
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(content));
        when(progresses.findByEnrollmentId(10L)).thenReturn(List.of(progress));
        when(exams.existsByCourseId(1L)).thenReturn(true);
        when(submissions.hasUnsatisfiedRequired(1L,10L)).thenReturn(true);
        for(int i=0;i<3;i++) {
            switch(order.charAt(i)) {
                case 'C' -> progress.updateProgress(BigDecimal.valueOf(100),CourseworkFixtures.NOW);
                case 'T' -> when(attempts.existsByEnrollmentIdAndSubmittedAtIsNotNullAndPassedTrue(10L)).thenReturn(true);
                case 'A' -> when(submissions.hasUnsatisfiedRequired(1L,10L)).thenReturn(false);
            }
            completion.evaluate(enrollment,CourseworkFixtures.NOW);
            assertThat(enrollment.getStatus()).isEqualTo(i==2 ? EnrollmentStatus.COMPLETED : EnrollmentStatus.IN_PROGRESS);
        }
        verify(notifications,times(1)).notify(enrollment,NotificationType.COURSE_COMPLETED);
    }
    @Test void pendingRequiredDefersExpiryThenPassCompletes() {
        when(submissions.hasPendingRequired(1L,10L)).thenReturn(true);
        assertThat(expiration.expire(10L,DUE.plusDays(1))).isFalse();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        when(submissions.hasPendingRequired(1L,10L)).thenReturn(false);
        completion.evaluate(enrollment,CourseworkFixtures.NOW.plusDays(1));
        assertThat(expiration.expire(10L,DUE.plusDays(2))).isFalse();
        verify(notifications).notify(enrollment,NotificationType.COURSE_COMPLETED);
        verify(notifications,never()).notify(enrollment,NotificationType.ENROLLMENT_EXPIRED);
    }
    @Test void failureAfterPendingReturnsToNormalExpiryFlow() {
        when(submissions.hasPendingRequired(1L,10L)).thenReturn(true);
        assertThat(expiration.expire(10L,DUE.plusDays(1))).isFalse();
        when(submissions.hasMissingOrFailedRequired(1L,10L)).thenReturn(true);
        assertThat(expiration.expire(10L,DUE.plusDays(1))).isTrue();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
        verify(notifications).notify(enrollment,NotificationType.ENROLLMENT_EXPIRED);
    }
    @ParameterizedTest @ValueSource(strings={"missing","failed","content","exam","optional-only"})
    void pendingIsNotEnoughToDeferExpiry(String reason) {
        when(submissions.hasPendingRequired(1L,10L)).thenReturn(!reason.equals("optional-only"));
        if(reason.equals("missing") || reason.equals("failed")) when(submissions.hasMissingOrFailedRequired(1L,10L)).thenReturn(true);
        if(reason.equals("exam")) when(exams.existsByCourseId(1L)).thenReturn(true);
        if(reason.equals("content")) {
            var c = CourseContent.create(course,"필수",ContentType.LINK,"https://example.com",null,1,true); id(c,50L);
            when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(c));
        }
        assertThat(expiration.expire(10L,DUE.plusDays(1))).isTrue();
    }
    @Test void dueDateDayNeverExpiresEvenWithFailedAssignment() {
        when(submissions.hasMissingOrFailedRequired(1L,10L)).thenReturn(true);
        assertThat(expiration.expire(10L,DUE)).isFalse();
    }
}

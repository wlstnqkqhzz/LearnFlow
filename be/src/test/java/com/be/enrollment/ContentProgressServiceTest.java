package com.be.enrollment;

import com.be.assignment.AssignmentFixtures;
import com.be.course.entity.*;
import com.be.course.repository.CourseContentRepository;
import com.be.enrollment.dto.ContentProgressUpdateRequest;
import com.be.enrollment.entity.*;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.*;
import com.be.enrollment.service.*;
import com.be.exam.repository.ExamRepository;
import com.be.global.exception.*;
import com.be.global.security.MemberPrincipal;
import com.be.member.enums.Role;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static com.be.enrollment.ProgressFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Service는 실제 Entity 및 실제 수료 판정기를 사용하고 저장소만 대체
class ContentProgressServiceTest {
    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final CourseContentRepository contents = mock(CourseContentRepository.class);
    final ContentProgressRepository progresses = mock(ContentProgressRepository.class);
    final ExamRepository exams = mock(ExamRepository.class);
    final ContentProgressService service = new ContentProgressService(enrollments, contents, progresses,
            new EnrollmentCompletionService(exams), AssignmentFixtures.CLOCK);
    Course course;
    Enrollment enrollment;
    CourseContent content;
    final Map<Long, ContentProgress> stored = new HashMap<>();

    @BeforeEach void setup() {
        course = course(); enrollment = enrollment(course); content = content(course, 1, true);
        when(enrollments.findById(10L)).thenReturn(Optional.of(enrollment));
        when(enrollments.findForProgressUpdate(10L)).thenReturn(Optional.of(enrollment));
        when(contents.findByIdAndCourseId(1L, 1L)).thenReturn(Optional.of(content));
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(content));
        when(progresses.findByEnrollmentIdAndCourseContentId(eq(10L), anyLong()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(1))));
        when(progresses.findByEnrollmentId(10L)).thenAnswer(call -> new ArrayList<>(stored.values()));
        doAnswer(call -> {
            ContentProgress progress = call.getArgument(0);
            if (progress.getId() == null) AssignmentFixtures.id(progress, 100L);
            stored.put(progress.getCourseContent().getId(), progress);
            return progress;
        }).when(progresses).saveAndFlush(any());
    }

    @Test void firstPositiveProgressStartsLearningAndUpdatesSameRow() {
        var result = update("35");
        assertThat(result.status()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        assertThat(result.startedAt()).isEqualTo(NOW);
        ContentProgress first = stored.get(1L);
        var next = update("50");
        assertThat(stored).hasSize(1);
        assertThat(stored.get(1L)).isSameAs(first);
        assertThat(next.progressRate()).isEqualByComparingTo("50");
        assertThat(next.startedAt()).isEqualTo(NOW);
        assertThat(next.completedAt()).isNull();
    }

    @Test void zeroDoesNotStartOrCompleteEvenWhenThresholdIsZero() {
        ReflectionTestUtils.setField(course, "passingProgressRate", BigDecimal.ZERO);
        var result = update("0");
        assertThat(result.status()).isEqualTo(EnrollmentStatus.ASSIGNED);
        assertThat(result.startedAt()).isNull();
        assertThat(result.completedAt()).isNull();
        assertThat(result.contentConditionSatisfied()).isTrue();
        assertThat(stored.get(1L).getCompletedAt()).isNull();
    }

    @Test void hundredSetsTimestampRepeatPreservesItAndDecreaseClearsIt() {
        when(exams.existsByCourseId(1L)).thenReturn(true);
        update("100");
        var firstCompleted = stored.get(1L).getCompletedAt();
        assertThat(firstCompleted).isEqualTo(NOW);
        ReflectionTestUtils.setField(stored.get(1L), "completedAt", NOW.minusMinutes(1));
        update("100.00");
        assertThat(stored.get(1L).getCompletedAt()).isEqualTo(NOW.minusMinutes(1));
        update("25");
        assertThat(stored.get(1L).getCompletedAt()).isNull();
        assertThat(enrollment.getStartedAt()).isEqualTo(NOW);
        update("0");
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @ParameterizedTest @ValueSource(strings = {"-0.01", "100.01", "20.001"})
    void invalidRateCannotBePersisted(String rate) {
        assertError(() -> update(rate), ErrorCode.INVALID_PROGRESS_RATE);
        verify(progresses, never()).saveAndFlush(any());
        assertThat(enrollment.getStartedAt()).isNull();
    }

    @Test void nullRateCannotBePersisted() {
        assertError(() -> service.update(10L, 1L, OWNER, new ContentProgressUpdateRequest(null)),
                ErrorCode.INVALID_PROGRESS_RATE);
    }

    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = {"COMPLETED", "FAILED", "EXPIRED"})
    void terminalStateRejectsNewAndExistingProgress(EnrollmentStatus status) {
        ReflectionTestUtils.setField(enrollment, "status", status);
        assertError(() -> update("50"), ErrorCode.ENROLLMENT_PROGRESS_NOT_EDITABLE);
        stored.put(1L, progress(enrollment, content, "25"));
        assertError(() -> update("50"), ErrorCode.ENROLLMENT_PROGRESS_NOT_EDITABLE);
        assertThat(stored.get(1L).getProgressRate()).isEqualByComparingTo("25");
        verifyNoInteractions(progresses);
    }

    @Test void contentMustBelongToEnrollmentCourse() {
        // 존재하지만 다른 과정 소속인 콘텐츠도 scoped 조회 결과 없음으로 처리
        when(contents.findByIdAndCourseId(99L, 1L)).thenReturn(Optional.empty());
        assertError(() -> service.update(10L, 99L, OWNER, new ContentProgressUpdateRequest(BigDecimal.TEN)),
                ErrorCode.COURSE_CONTENT_NOT_FOUND);
        verifyNoInteractions(progresses);
    }

    @Test void missingEnrollmentReturnsBusinessError() {
        assertError(() -> service.get(999L, OWNER), ErrorCode.ENROLLMENT_NOT_FOUND);
        assertError(() -> service.update(999L, 1L, OWNER, new ContentProgressUpdateRequest(BigDecimal.TEN)),
                ErrorCode.ENROLLMENT_NOT_FOUND);
    }

    @Test void missingRowsIncludedWithoutWritingDuringRead() {
        var optional = content(course, 2, false);
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(content, optional));
        var result = service.get(10L, OWNER);
        assertThat(result.contents()).hasSize(2);
        assertThat(result.contents()).allSatisfy(c -> {
            assertThat(c.progressRate()).isEqualByComparingTo("0");
            assertThat(c.completedAt()).isNull();
        });
        assertThat(result.progressRate()).isEqualByComparingTo("0");
        verify(progresses, never()).saveAndFlush(any());
        verify(enrollments, never()).findForProgressUpdate(anyLong());
    }

    @Test void otherEmployeeCannotReadOrWriteAdminCanOnlyReadOthers() {
        var other = new MemberPrincipal(3L, "other@example.com", Set.of(Role.EMPLOYEE));
        var admin = new MemberPrincipal(3L, "admin@example.com", Set.of(Role.EMPLOYEE, Role.ADMIN));
        assertError(() -> service.get(10L, other), ErrorCode.ENROLLMENT_PROGRESS_ACCESS_DENIED);
        assertError(() -> service.update(10L, 1L, other, new ContentProgressUpdateRequest(BigDecimal.TEN)),
                ErrorCode.ENROLLMENT_PROGRESS_ACCESS_DENIED);
        assertThat(service.get(10L, admin).enrollmentId()).isEqualTo(10L);
        assertError(() -> service.update(10L, 1L, admin, new ContentProgressUpdateRequest(BigDecimal.TEN)),
                ErrorCode.ENROLLMENT_PROGRESS_ACCESS_DENIED);
    }

    @Test void instructorAloneCannotAccessEvenOwnProgress() {
        var instructor = new MemberPrincipal(2L, "i@example.com", Set.of(Role.INSTRUCTOR));
        assertError(() -> service.get(10L, instructor), ErrorCode.ENROLLMENT_PROGRESS_ACCESS_DENIED);
    }

    @Test void noExamCompletesAtExactThresholdAndBlocksSubsequentWrites() {
        var result = update("80");
        assertThat(result.status()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(result.startedAt()).isEqualTo(NOW);
        assertThat(result.completedAt()).isEqualTo(NOW);
        assertThat(result.contents().getFirst().completedAt()).isNull();
        assertError(() -> update("100"), ErrorCode.ENROLLMENT_PROGRESS_NOT_EDITABLE);
    }

    @Test void examPreventsCompletionEvenAtHundred() {
        when(exams.existsByCourseId(1L)).thenReturn(true);
        var result = update("100");
        assertThat(result.status()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        assertThat(result.contentConditionSatisfied()).isTrue();
        assertThat(result.completedAt()).isNull();
    }

    @Test void noRequiredContentsSatisfiedButZeroDoesNotStartAndReadNeverCompletes() {
        content.update("선택", content.getContentType(), content.getContentUrl(), null, 1, false);
        assertThat(service.get(10L, OWNER).contentConditionSatisfied()).isTrue();
        assertThat(update("0").status()).isEqualTo(EnrollmentStatus.ASSIGNED);
        assertThat(update("1").status()).isEqualTo(EnrollmentStatus.COMPLETED);
    }

    @Test void pastDueDateDoesNotIntroduceExpiryPolicy() {
        ReflectionTestUtils.setField(enrollment, "dueDate", NOW.toLocalDate().minusDays(2));
        assertThat(update("20").status()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test void optionalProgressDoesNotInflateRequiredAverage() {
        var second = content(course, 2, true);
        var optional = content(course, 3, false);
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(content, second, optional));
        stored.put(3L, progress(enrollment, optional, "100"));
        var result = update("100");
        assertThat(result.progressRate()).isEqualByComparingTo("50");
        assertThat(result.contents()).hasSize(3);
        assertThat(result.contents().get(1).progressRate()).isEqualByComparingTo("0");
        assertThat(result.status()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test void optionalOnlyWithExamRemainsInProgress() {
        content.update("선택", content.getContentType(), content.getContentUrl(), null, 1, false);
        when(exams.existsByCourseId(1L)).thenReturn(true);
        var result = update("1");
        assertThat(result.contentConditionSatisfied()).isTrue();
        assertThat(result.status()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test void concurrentInsertIntegrityFailureIsNotSwallowedOrRetried() {
        var failure = new org.springframework.dao.DataIntegrityViolationException("unique progress");
        doThrow(failure).when(progresses).saveAndFlush(any());
        assertThatThrownBy(() -> update("20")).isSameAs(failure);
        verify(progresses).saveAndFlush(any());
        verifyNoInteractions(exams);
    }

    @Test void optimisticConflictIsPropagatedWithoutRetry() {
        var failure = new ObjectOptimisticLockingFailureException(ContentProgress.class, 100L);
        doThrow(failure).when(progresses).saveAndFlush(any());
        assertThatThrownBy(() -> update("10")).isSameAs(failure);
        verify(progresses).saveAndFlush(any());
        // DB 롤백 자체는 이 단위 테스트의 검증 범위가 아님
        verifyNoInteractions(exams);
    }

    private com.be.enrollment.dto.EnrollmentProgressResponse update(String rate) {
        return service.update(10L, 1L, OWNER, new ContentProgressUpdateRequest(new BigDecimal(rate)));
    }
    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
}

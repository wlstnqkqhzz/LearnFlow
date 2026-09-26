package com.be.enrollment;

import com.be.course.enums.ContentType;
import com.be.enrollment.dto.ContentProgressResponse;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.service.EnrollmentCompletionService;
import com.be.exam.repository.ExamRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.be.enrollment.ProgressFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 필수 콘텐츠 합계 기반 정확한 판정과 상태 전이 경계 검증
class EnrollmentCompletionServiceTest {
    final ExamRepository exams = mock(ExamRepository.class);
    final EnrollmentCompletionService service = new EnrollmentCompletionService(exams, mock(com.be.exam.repository.ExamAttemptRepository.class), mock(com.be.course.repository.CourseContentRepository.class), mock(com.be.enrollment.repository.ContentProgressRepository.class), mock(com.be.notification.service.NotificationService.class));

    @Test void missingRequiredCountsAsZeroOptionalExcluded() {
        var result = service.summarize(List.of(item("100", true), item("50", true),
                item("0", true), item("100", false)), new BigDecimal("80"));
        assertThat(result.progressRate()).isEqualByComparingTo("50");
        assertThat(result.contentConditionSatisfied()).isFalse();
    }

    @ParameterizedTest @CsvSource({"79.99,false", "80,true", "100,true"})
    void exactThreshold(String rate, boolean satisfied) {
        var result = service.summarize(List.of(item(rate, true)), new BigDecimal("80"));
        assertThat(result.contentConditionSatisfied()).isEqualTo(satisfied);
    }

    @Test void repeatingAverageDoesNotRoundUpToPass() {
        var result = service.summarize(List.of(item("100", true), item("100", true), item("0", true)),
                new BigDecimal("66.67"));
        assertThat(result.progressRate()).isEqualByComparingTo("66.66");
        assertThat(result.contentConditionSatisfied()).isFalse();
    }

    @Test void emptyAndOnlyOptionalContentsSatisfyCondition() {
        assertThat(service.summarize(List.of(), new BigDecimal("100")).contentConditionSatisfied()).isTrue();
        var result = service.summarize(List.of(item("0", false)), new BigDecimal("100"));
        assertThat(result.progressRate()).isEqualByComparingTo("100");
        assertThat(result.contentConditionSatisfied()).isTrue();
    }

    @Test void completionPreservesFirstTimestampAndNeverCompletesAssigned() {
        var enrollment = enrollment(course());
        var fulfilled = new EnrollmentCompletionService.ProgressSummary(new BigDecimal("100"), true);
        service.evaluate(enrollment, fulfilled, NOW);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ASSIGNED);
        verifyNoInteractions(exams);
        enrollment.startLearning(NOW.minusMinutes(5));
        service.evaluate(enrollment, fulfilled, NOW);
        service.evaluate(enrollment, fulfilled, NOW.plusDays(1));
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getCompletedAt()).isEqualTo(NOW);
        assertThat(enrollment.getStartedAt()).isEqualTo(NOW.minusMinutes(5));
        verify(exams, times(1)).existsByCourseId(1L);
    }

    private ContentProgressResponse item(String rate, boolean required) {
        return new ContentProgressResponse(1L, "콘텐츠", ContentType.LINK, "https://example.com", null, required, 1,
                new BigDecimal(rate), null);
    }
}

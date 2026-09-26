package com.be.enrollment.service;

import com.be.enrollment.dto.ContentProgressResponse;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.exam.repository.ExamRepository;
import com.be.exam.repository.ExamAttemptRepository;
import com.be.course.repository.CourseContentRepository;
import com.be.enrollment.repository.ContentProgressRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 콘텐츠 변경과 시험 합격 제출에서 공통으로 사용하는 최종 수료 판정
@Service
@RequiredArgsConstructor
public class EnrollmentCompletionService {
    private final ExamRepository exams;
    private final ExamAttemptRepository attempts;
    private final CourseContentRepository contents;
    private final ContentProgressRepository progresses;
    private final com.be.notification.service.NotificationService notifications;

    // 시험 제출에서도 누락 진도를 0으로 포함하는 동일 요약/판정을 재사용
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(Enrollment enrollment, LocalDateTime now) {
        var byContent = progresses.findByEnrollmentId(enrollment.getId()).stream().collect(
                java.util.stream.Collectors.toMap(p -> p.getCourseContent().getId(), p -> p));
        var items = contents.findByCourseIdOrderBySortOrderAsc(enrollment.getCourse().getId()).stream()
                .map(c -> ContentProgressResponse.from(c, byContent.get(c.getId()))).toList();
        evaluate(enrollment, summarize(items, enrollment.getCourse().getPassingProgressRate()), now);
    }

    // 미생성 진도가 0으로 채워진 전체 콘텐츠를 기준으로 필수 콘텐츠만 평균 계산
    public ProgressSummary summarize(List<ContentProgressResponse> contents, BigDecimal passingRate) {
        var required = contents.stream().filter(ContentProgressResponse::required).toList();
        if (required.isEmpty()) return new ProgressSummary(new BigDecimal("100.00"), true);
        BigDecimal sum = required.stream().map(ContentProgressResponse::progressRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal count = BigDecimal.valueOf(required.size());
        // 판정은 나눗셈/반올림 없이 비교하여 기준 미달을 반올림으로 합격시키지 않음
        boolean satisfied = sum.compareTo(passingRate.multiply(count)) >= 0;
        return new ProgressSummary(sum.divide(count, 2, RoundingMode.DOWN), satisfied);
    }

    // 호출자가 수강 버전을 잠그고 진도 변경과 같은 트랜잭션에서 평가해야 함
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(Enrollment enrollment, ProgressSummary summary, LocalDateTime now) {
        if (enrollment.getStatus() == EnrollmentStatus.IN_PROGRESS && summary.contentConditionSatisfied()
                && (!exams.existsByCourseId(enrollment.getCourse().getId())
                    || attempts.existsByEnrollmentIdAndSubmittedAtIsNotNullAndPassedTrue(enrollment.getId()))) {
            enrollment.completeLearning(now);
            notifications.notify(enrollment, com.be.notification.enums.NotificationType.COURSE_COMPLETED);
        }
    }

    // 평균은 응답 표시용 소수 둘째 자리, 만족 여부는 정확한 배점 합계 비교 결과
    public record ProgressSummary(BigDecimal progressRate, boolean contentConditionSatisfied) {}
}

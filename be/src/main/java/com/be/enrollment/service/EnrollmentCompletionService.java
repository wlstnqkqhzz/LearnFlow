package com.be.enrollment.service;

import com.be.enrollment.dto.ContentProgressResponse;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.exam.repository.ExamRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 학습 조건 계산과 수료 판정 책임 분리; 추후 시험 제출 트랜잭션에서도 재사용
@Service
@RequiredArgsConstructor
public class EnrollmentCompletionService {
    private final ExamRepository exams;

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
                && !exams.existsByCourseId(enrollment.getCourse().getId())) {
            enrollment.completeLearning(now);
        }
    }

    // 평균은 응답 표시용 소수 둘째 자리, 만족 여부는 정확한 배점 합계 비교 결과
    public record ProgressSummary(BigDecimal progressRate, boolean contentConditionSatisfied) {}
}

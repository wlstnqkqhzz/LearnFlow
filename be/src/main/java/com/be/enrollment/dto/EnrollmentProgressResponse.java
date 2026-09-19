package com.be.enrollment.dto;

import com.be.enrollment.enums.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 전체 진도와 실제 판정 결과를 함께 제공 (표시용 평균과 수료 판정은 분리)
public record EnrollmentProgressResponse(Long enrollmentId, Long courseId, EnrollmentStatus status,
        BigDecimal progressRate, BigDecimal passingProgressRate, boolean contentConditionSatisfied,
        LocalDateTime startedAt, LocalDateTime completedAt, List<ContentProgressResponse> contents) {
}

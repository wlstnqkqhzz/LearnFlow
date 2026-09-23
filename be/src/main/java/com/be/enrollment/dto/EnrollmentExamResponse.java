package com.be.enrollment.dto;

import com.be.exam.entity.Exam;
import java.math.BigDecimal;

// 소유권 검증 후 제공하는 학습용 시험 요약; 문항과 정답 정보는 포함하지 않음
public record EnrollmentExamResponse(Long examId, String title, BigDecimal passingScore, int maxAttempts) {
    public static EnrollmentExamResponse from(Exam exam) {
        return new EnrollmentExamResponse(exam.getId(), exam.getTitle(), exam.getPassingScore(), exam.getMaxAttempts());
    }
}

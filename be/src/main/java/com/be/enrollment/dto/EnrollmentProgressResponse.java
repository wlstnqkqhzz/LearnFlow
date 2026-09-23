package com.be.enrollment.dto;

import com.be.enrollment.enums.EnrollmentStatus;
import com.be.course.enums.CourseType;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

// 전체 진도와 실제 판정 결과를 함께 제공 (표시용 평균과 수료 판정은 분리)
public record EnrollmentProgressResponse(Long enrollmentId, EnrollmentStatus status, LocalDate dueDate,
        LocalDateTime assignedAt, LocalDateTime startedAt, LocalDateTime completedAt,
        Long courseId, String courseTitle, String courseDescription, CourseType courseType,
        LocalDate courseStartDate, LocalDate courseEndDate,
        Long instructorId, String instructorName,
        BigDecimal progressRate, BigDecimal passingProgressRate, boolean contentConditionSatisfied,
        List<ContentProgressResponse> contents, EnrollmentExamResponse exam) {
}

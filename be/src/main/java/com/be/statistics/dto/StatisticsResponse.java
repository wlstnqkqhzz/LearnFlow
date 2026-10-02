package com.be.statistics.dto;

import com.be.course.enums.CourseStatus;
import com.be.global.dto.PageResponse;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class StatisticsResponse {
    private StatisticsResponse() {}
    public record Meta(LocalDate startDate, LocalDate endDate, String timeZone, Instant generatedAt,
                       Long courseId, Long departmentId, String departmentScope) {}
    public record Overview(Meta meta, long assignedCount, long completedCount,
                           long cohortCompletedCount, long cohortFailedCount, long cohortExpiredCount,
                           BigDecimal completionRate, BigDecimal averageCompletionDays) {}
    public record Exam(Long examId, long submittedAttemptCount, long passedAttemptCount,
                       BigDecimal averageScore, BigDecimal attemptPassRate) {}
    // Status counts describe the current outcome of the assignedAt cohort, not historical snapshots.
    public record CourseRow(Long courseId, String title, CourseStatus status, Long retrainingPolicyId,
                            Integer occurrenceNumber, long assignedCount, long notStartedCount,
                            long inProgressCount, long completedCount, long failedCount, long expiredCount,
                            BigDecimal completionRate, Exam exam) {}
    public record DepartmentRow(Long departmentId, String departmentName, Long parentDepartmentId,
                                boolean active, long currentEmployeeCount, long assignedCount,
                                long completedCount, long failedCount, long expiredCount, BigDecimal completionRate) {}
    public record Courses(Meta meta, PageResponse<CourseRow> data) {}
    public record Departments(Meta meta, PageResponse<DepartmentRow> data) {}
    public enum Granularity { DAY, MONTH }
    public record TrendPoint(LocalDate bucketStart, LocalDate periodStart, LocalDate periodEnd,
                             long assignments, long completions) {}
    public record Trends(Meta meta, Granularity granularity, List<TrendPoint> points) {}
}

package com.be.dashboard.dto;

import com.be.course.enums.CourseType;
import com.be.enrollment.enums.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 대시보드 한 번의 요청에 필요한 집계와 제한된 최근 항목만 반환한다.
public record DashboardResponse(Overview overview, Distribution distribution, List<ActiveCourse> activeCourses,
                                Attention attention, List<Completion> recentCompletions, List<Assignment> recentAssignments) {
    public record Overview(long employeeCount, long openCourseCount, long ongoingEnrollmentCount, BigDecimal completionRate) {}
    public record StatusCount(EnrollmentStatus status, long count) {}
    public record Distribution(long total, List<StatusCount> counts) {}
    public record ActiveCourse(Long courseId, String title, CourseType type, long enrollmentCount,
                               long completedCount, BigDecimal completionRate) {}
    public record Attention(long dueSoonCount, long failedCount, long expiredCount) {}
    public record Completion(Long enrollmentId, String memberName, String courseTitle, LocalDateTime completedAt) {}
    public record Assignment(Long enrollmentId, String memberName, String departmentName, String courseTitle,
                             EnrollmentStatus status, LocalDateTime assignedAt) {}
}

package com.be.dashboard.service;

import com.be.dashboard.dto.DashboardResponse;
import com.be.dashboard.dto.DashboardResponse.*;
import com.be.dashboard.repository.DashboardRepository;
import com.be.course.enums.CourseStatus;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.member.enums.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {
    private final DashboardRepository repository;
    private final Clock clock;

    public DashboardResponse get() {
        var counts = new EnumMap<EnrollmentStatus, Long>(EnrollmentStatus.class);
        for (var status : EnrollmentStatus.values()) counts.put(status, 0L);
        for (var row : repository.statusCounts()) counts.put(row.getStatus(), row.getCount());
        // 최대 다섯 집계 행만 조립한다. Entity 전체를 읽어 집계하지 않는다.
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        var overview = new Overview(repository.employeeCount(Role.EMPLOYEE, List.of(MemberStatus.ACTIVE, MemberStatus.ON_LEAVE)),
                repository.courseCount(CourseStatus.OPEN), counts.get(EnrollmentStatus.ASSIGNED) + counts.get(EnrollmentStatus.IN_PROGRESS),
                rate(counts.get(EnrollmentStatus.COMPLETED), total));
        var distribution = new Distribution(total, counts.entrySet().stream().map(e -> new StatusCount(e.getKey(), e.getValue())).toList());
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        var attention = new Attention(repository.dueSoonCount(List.of(EnrollmentStatus.ASSIGNED, EnrollmentStatus.IN_PROGRESS), today, today.plusDays(7)),
                counts.get(EnrollmentStatus.FAILED), counts.get(EnrollmentStatus.EXPIRED));
        var courses = repository.activeCourses(PageRequest.of(0, 4)).stream()
                .map(c -> new ActiveCourse(c.getCourseId(), c.getTitle(), c.getType(), c.getEnrollmentCount(), c.getCompletedCount(),
                        rate(c.getCompletedCount(), c.getEnrollmentCount()))).toList();
        var completions = repository.recentCompletions(PageRequest.of(0, 3)).stream()
                .map(e -> new Completion(e.getEnrollmentId(), e.getMemberName(), e.getCourseTitle(), e.getCompletedAt())).toList();
        var assignments = repository.recentAssignments(PageRequest.of(0, 6)).stream()
                .map(e -> new Assignment(e.getEnrollmentId(), e.getMemberName(), e.getDepartmentName(), e.getCourseTitle(), e.getStatus(), e.getAssignedAt())).toList();
        return new DashboardResponse(overview, distribution, courses, attention, completions, assignments);
    }

    // 0건은 0%, 그 외 소수점 둘째 자리 HALF_UP으로 표시한다.
    private BigDecimal rate(long completed, long total) {
        return total == 0 ? new BigDecimal("0.00") : BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}

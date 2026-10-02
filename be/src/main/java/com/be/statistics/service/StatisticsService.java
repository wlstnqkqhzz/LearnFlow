package com.be.statistics.service;

import com.be.global.dto.PageResponse;
import com.be.statistics.dto.*;
import com.be.statistics.dto.StatisticsFilter.Range;
import com.be.statistics.dto.StatisticsResponse.*;
import com.be.statistics.repository.StatisticsRepository;
import com.be.statistics.repository.StatisticsRepository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @RequiredArgsConstructor @Validated @Transactional(readOnly = true)
public class StatisticsService {
    private final StatisticsRepository repository;
    private final Clock clock;
    private Meta meta(Range range) {
        var f = range.filter();
        return new Meta(f.startDate(), f.endDate(), "Asia/Seoul", clock.instant(), f.courseId(), f.departmentId(), "DIRECT");
    }
    private static BigDecimal rate(long numerator, long denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
    private static BigDecimal rounded(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }
    private static <T> PageResponse<T> page(List<T> rows, StatisticsPageRequest p, long total) {
        return new PageResponse<>(List.copyOf(rows), p.page(), p.size(), total, Math.toIntExact((total + p.size() - 1) / p.size()));
    }
    public Overview overview(@NotNull @Valid StatisticsFilter filter) {
        var range = filter.resolve(clock);
        var cohort = repository.cohort(range);
        var completions = repository.completions(range);
        var days = completions.averageMicroseconds() == null ? null
                : completions.averageMicroseconds().divide(new BigDecimal("86400000000"), 2, RoundingMode.HALF_UP);
        return new Overview(meta(range), cohort.total(), completions.count(), cohort.completed(), cohort.failed(), cohort.expired(),
                rate(cohort.completed(), cohort.total()), days);
    }
    public Courses courses(@NotNull @Valid StatisticsFilter filter, @NotNull @Valid StatisticsPageRequest p) {
        var range = filter.resolve(clock);
        long total = repository.courseCount(range);
        var courses = repository.courses(range, p);
        var ids = courses.stream().map(CourseInfo::id).toList();
        var cohorts = repository.courseCohorts(range, ids);
        var exams = repository.exams(range, ids);
        var rows = courses.stream().map(c -> {
            var a = cohorts.getOrDefault(c.id(), Cohort.EMPTY);
            var x = exams.getOrDefault(c.id(), new ExamCount(0, 0, null));
            var exam = c.examId() == null ? null : new Exam(c.examId(), x.submitted(), x.passed(), rounded(x.averageScore()), rate(x.passed(), x.submitted()));
            return new CourseRow(c.id(), c.title(), c.status(), c.policyId(), c.occurrenceNumber(), a.total(), a.notStarted(),
                    a.inProgress(), a.completed(), a.failed(), a.expired(), rate(a.completed(), a.total()), exam);
        }).toList();
        return new Courses(meta(range), page(rows, p, total));
    }
    public Departments departments(@NotNull @Valid StatisticsFilter filter, @NotNull @Valid StatisticsPageRequest p) {
        var range = filter.resolve(clock);
        long total = repository.departmentCount(range);
        var departments = repository.departments(range, p);
        var ids = departments.stream().map(DepartmentInfo::id).toList();
        var cohorts = repository.departmentCohorts(range, ids);
        var employees = repository.employees(ids);
        var rows = departments.stream().map(d -> {
            var a = cohorts.getOrDefault(d.id(), Cohort.EMPTY);
            return new DepartmentRow(d.id(), d.name(), d.parentId(), d.active(), employees.getOrDefault(d.id(), 0L),
                    a.total(), a.completed(), a.failed(), a.expired(), rate(a.completed(), a.total()));
        }).toList();
        return new Departments(meta(range), page(rows, p, total));
    }
    public Trends trends(@NotNull @Valid StatisticsFilter filter) {
        var range = filter.resolve(clock);
        boolean monthly = range.days() > 62;
        Map<LocalDate, Long> assigned = new HashMap<>(), completed = new HashMap<>();
        repository.trend(range, false, monthly).forEach(b -> assigned.put(b.date(), b.count()));
        repository.trend(range, true, monthly).forEach(b -> completed.put(b.date(), b.count()));
        List<TrendPoint> points = new ArrayList<>();
        LocalDate first = monthly ? filter.startDate().withDayOfMonth(1) : filter.startDate();
        for (LocalDate date = first; !date.isAfter(filter.endDate()); date = monthly ? date.plusMonths(1) : date.plusDays(1)) {
            LocalDate end = monthly ? date.plusMonths(1).minusDays(1) : date;
            points.add(new TrendPoint(date, date.isBefore(filter.startDate()) ? filter.startDate() : date,
                    end.isAfter(filter.endDate()) ? filter.endDate() : end, assigned.getOrDefault(date, 0L), completed.getOrDefault(date, 0L)));
        }
        return new Trends(meta(range), monthly ? Granularity.MONTH : Granularity.DAY, List.copyOf(points));
    }
}

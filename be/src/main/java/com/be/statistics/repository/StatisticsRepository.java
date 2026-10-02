package com.be.statistics.repository;

import com.be.course.enums.CourseStatus;
import com.be.statistics.dto.StatisticsFilter.Range;
import com.be.statistics.dto.StatisticsPageRequest;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import com.be.statistics.dto.StatisticsFilter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

/** Read-only SQL projections. No managed entities or per-row queries. */
@Repository
@RequiredArgsConstructor
public class StatisticsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String ENROLLMENTS = " FROM enrollments e JOIN members m ON m.id=e.member_id ";
    private static final String COUNTS = """
        COUNT(*) AS total,
        COALESCE(SUM(CASE WHEN e.status='ASSIGNED' THEN 1 ELSE 0 END),0) AS not_started,
        COALESCE(SUM(CASE WHEN e.status='IN_PROGRESS' THEN 1 ELSE 0 END),0) AS in_progress,
        COALESCE(SUM(CASE WHEN e.status='COMPLETED' THEN 1 ELSE 0 END),0) AS completed,
        COALESCE(SUM(CASE WHEN e.status='FAILED' THEN 1 ELSE 0 END),0) AS failed,
        COALESCE(SUM(CASE WHEN e.status='EXPIRED' THEN 1 ELSE 0 END),0) AS expired
        """;
    private static final RowMapper<Cohort> COHORT = (r, n) -> new Cohort(r.getLong("total"),
            r.getLong("not_started"), r.getLong("in_progress"), r.getLong("completed"), r.getLong("failed"), r.getLong("expired"));
    public record Cohort(long total, long notStarted, long inProgress, long completed, long failed, long expired) {
        public static final Cohort EMPTY = new Cohort(0, 0, 0, 0, 0, 0);
    }
    public record Completions(long count, BigDecimal averageMicroseconds) {}
    public record CourseInfo(long id, String title, CourseStatus status, Long policyId, Integer occurrenceNumber, Long examId) {}
    public record DepartmentInfo(long id, String name, Long parentId, boolean active) {}
    public record ExamCount(long submitted, long passed, BigDecimal averageScore) {}
    public record Bucket(LocalDate date, long count) {}

    private MapSqlParameterSource params(Range range) {
        return new MapSqlParameterSource("start", Timestamp.valueOf(range.startUtc()))
                .addValue("end", Timestamp.valueOf(range.endUtcExclusive()))
                .addValue("courseId", range.filter().courseId()).addValue("departmentId", range.filter().departmentId());
    }
    private String scope(Range range) {
        return (range.filter().courseId() == null ? "" : " AND e.course_id=:courseId")
                + (range.filter().departmentId() == null ? "" : " AND m.department_id=:departmentId");
    }
    private String assigned(Range range) {
        return " WHERE e.assigned_at>=:start AND e.assigned_at<:end" + scope(range);
    }
    public Cohort cohort(Range range) {
        return jdbc.queryForObject("SELECT " + COUNTS + ENROLLMENTS + assigned(range), params(range), COHORT);
    }
    public Completions completions(Range range) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) AS total, AVG(TIMESTAMPDIFF(MICROSECOND,e.assigned_at,e.completed_at)) AS duration
            """ + ENROLLMENTS + " WHERE e.status='COMPLETED' AND e.completed_at>=:start AND e.completed_at<:end" + scope(range),
                params(range), (r, n) -> new Completions(r.getLong("total"), r.getBigDecimal("duration")));
    }
    private String courseWhere(Range range) {
        return " WHERE c.status IN ('OPEN','CLOSED')" + (range.filter().courseId() == null ? "" : " AND c.id=:courseId");
    }
    public long courseCount(Range range) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM courses c" + courseWhere(range), params(range), Long.class);
    }
    public List<CourseInfo> courses(Range range, StatisticsPageRequest page) {
        return jdbc.query("""
            SELECT c.id,c.title,c.status,c.retraining_policy_id,c.occurrence_number,x.id AS exam_id
            FROM courses c LEFT JOIN exams x ON x.course_id=c.id
            """ + courseWhere(range) + " ORDER BY c.id LIMIT :limit OFFSET :offset",
                params(range).addValue("limit", page.size()).addValue("offset", (long) page.page() * page.size()),
                (r, n) -> new CourseInfo(r.getLong("id"), r.getString("title"), CourseStatus.valueOf(r.getString("status")),
                        r.getObject("retraining_policy_id", Long.class), r.getObject("occurrence_number", Integer.class), r.getObject("exam_id", Long.class)));
    }
    public Map<Long, Cohort> courseCohorts(Range range, List<Long> ids) { return groupedCohorts(range, ids, false); }
    public Map<Long, Cohort> departmentCohorts(Range range, List<Long> ids) { return groupedCohorts(range, ids, true); }
    private Map<Long, Cohort> groupedCohorts(Range range, List<Long> ids, boolean departments) {
        if (ids.isEmpty()) return Map.of();
        String group = departments ? "m.department_id" : "e.course_id";
        Map<Long, Cohort> result = new HashMap<>();
        jdbc.query("SELECT " + group + " AS group_id," + COUNTS + ENROLLMENTS + assigned(range)
                + " AND " + group + " IN (:ids) GROUP BY " + group, params(range).addValue("ids", ids),
                (org.springframework.jdbc.core.RowCallbackHandler) r -> result.put(r.getLong("group_id"), COHORT.mapRow(r, 0)));
        return result;
    }
    // Independent submittedAt population, batched for exactly the current course page.
    public Map<Long, ExamCount> exams(Range range, List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, ExamCount> result = new HashMap<>();
        jdbc.query("""
            SELECT e.course_id,COUNT(*) AS submitted,
              COALESCE(SUM(CASE WHEN a.passed=TRUE THEN 1 ELSE 0 END),0) AS passed,AVG(a.score) AS average_score
            FROM exam_attempts a JOIN enrollments e ON e.id=a.enrollment_id JOIN members m ON m.id=e.member_id
            WHERE a.submitted_at>=:start AND a.submitted_at<:end AND e.course_id IN (:ids)
            """ + scope(range) + " GROUP BY e.course_id", params(range).addValue("ids", ids),
                (org.springframework.jdbc.core.RowCallbackHandler) r -> result.put(r.getLong("course_id"),
                        new ExamCount(r.getLong("submitted"), r.getLong("passed"), r.getBigDecimal("average_score"))));
        return result;
    }
    private String departmentWhere(Range range) {
        return range.filter().departmentId() == null ? "" : " WHERE d.id=:departmentId";
    }
    public long departmentCount(Range range) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM departments d" + departmentWhere(range), params(range), Long.class);
    }
    public List<DepartmentInfo> departments(Range range, StatisticsPageRequest page) {
        return jdbc.query("SELECT d.id,d.name,d.parent_department_id,d.is_active FROM departments d" + departmentWhere(range)
                + " ORDER BY d.id LIMIT :limit OFFSET :offset",
                params(range).addValue("limit", page.size()).addValue("offset", (long) page.page() * page.size()),
                (r, n) -> new DepartmentInfo(r.getLong("id"), r.getString("name"), r.getObject("parent_department_id", Long.class), r.getBoolean("is_active")));
    }
    // Current headcount is independent of the period/course; EXISTS prevents role multiplicity.
    public Map<Long, Long> employees(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Long> result = new HashMap<>();
        jdbc.query("""
            SELECT m.department_id,COUNT(*) AS total FROM members m
            WHERE m.department_id IN (:ids) AND m.status IN ('ACTIVE','ON_LEAVE')
              AND EXISTS (SELECT 1 FROM member_roles r WHERE r.member_id=m.id AND r.role='EMPLOYEE')
            GROUP BY m.department_id
            """, new MapSqlParameterSource("ids", ids),
                (org.springframework.jdbc.core.RowCallbackHandler) r -> result.put(r.getLong("department_id"), r.getLong("total")));
        return result;
    }
    public List<Bucket> trend(Range range, boolean completion, boolean monthly) {
        // Small bounded CASE groups (<=62 days / 13 months). Java resolves Seoul boundaries,
        // including historical offset changes, without relying on MySQL time-zone table installation.
        String column = completion ? "e.completed_at" : "e.assigned_at";
        List<LocalDate> dates = new ArrayList<>();
        for (var date = monthly ? range.filter().startDate().withDayOfMonth(1) : range.filter().startDate();
             !date.isAfter(range.filter().endDate()); date = monthly ? date.plusMonths(1) : date.plusDays(1)) dates.add(date);
        var parameters = params(range);
        StringBuilder group = new StringBuilder();
        if (dates.size() == 1) group.append("0");
        else {
            group.append("CASE");
            for (int i = 0; i < dates.size() - 1; i++) {
                var boundary = dates.get(i + 1).atStartOfDay(StatisticsFilter.SEOUL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                parameters.addValue("boundary" + i, Timestamp.valueOf(boundary));
                group.append(" WHEN ").append(column).append("<:boundary").append(i).append(" THEN ").append(i);
            }
            group.append(" ELSE ").append(dates.size() - 1).append(" END");
        }
        return jdbc.query("SELECT " + group + " AS bucket_index,COUNT(*) AS total" + ENROLLMENTS
                + " WHERE " + column + ">=:start AND " + column + "<:end"
                + (completion ? " AND e.status='COMPLETED'" : "") + scope(range)
                + " GROUP BY bucket_index ORDER BY bucket_index", parameters,
                (r, n) -> new Bucket(dates.get(r.getInt("bucket_index")), r.getLong("total")));
    }
}

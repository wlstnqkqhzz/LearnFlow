package com.be.dashboard;

import com.be.dashboard.repository.DashboardRepository;
import com.be.dashboard.service.DashboardService;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.global.config.MemberSupportConfig;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

// 명시적으로 실행할 때만 기존 local DB에 SELECT한다. 스키마 생성/초기화/테스트 데이터 INSERT 없음.
@EnabledIfSystemProperty(named = "dashboard.local-test", matches = "true")
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=never", "spring.jpa.show-sql=false", "spring.datasource.hikari.read-only=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@Import({DashboardService.class, MemberSupportConfig.class})
@Transactional(readOnly = true)
class DashboardLocalDataTest {
    @Autowired EntityManager em;
    @Autowired DashboardRepository repository;
    @Autowired DashboardService service;

    @Test void aggregatesMatchIndependentSqlOnExistingData() {
        var data = service.get();
        assertThat(data.overview().employeeCount()).isEqualTo(count("select count(*) from members m where m.status in ('ACTIVE','ON_LEAVE') and exists (select 1 from member_roles r where r.member_id=m.id and r.role='EMPLOYEE')"));
        assertThat(data.overview().openCourseCount()).isEqualTo(count("select count(*) from courses where status='OPEN'"));
        assertThat(data.overview().ongoingEnrollmentCount()).isEqualTo(count("select count(*) from enrollments where status in ('ASSIGNED','IN_PROGRESS')"));
        assertThat(data.distribution().total()).isEqualTo(count("select count(*) from enrollments"));
        for (var row : data.distribution().counts()) {
            assertThat(row.count()).isEqualTo(((Number) em.createNativeQuery("select count(*) from enrollments where status=:status")
                    .setParameter("status", row.status().name()).getSingleResult()).longValue());
        }
        var rate = (Number) em.createNativeQuery("select coalesce(round(100.0 * sum(case when status='COMPLETED' then 1 else 0 end)/nullif(count(*),0),2),0) from enrollments").getSingleResult();
        assertThat(data.overview().completionRate()).isEqualByComparingTo(rate.toString());
        System.out.println("Dashboard local read-only check: employees=" + data.overview().employeeCount() + ", courses=" + data.overview().openCourseCount() + ", enrollments=" + data.distribution().total());
    }
    @Test void courseAggregationIncludesZeroEnrollmentCoursesAndMatchesSql() {
        var data = service.get();
        assertThat(data.activeCourses()).extracting(c -> c.courseId()).containsExactlyElementsOf(ids("select id from courses where status='OPEN' order by created_at desc,id desc limit 4"));
        for (var row : data.activeCourses()) {
            var counts = (Object[]) em.createNativeQuery("select count(*),coalesce(sum(case when status='COMPLETED' then 1 else 0 end),0) from enrollments where course_id=:id")
                    .setParameter("id", row.courseId()).getSingleResult();
            assertThat(row.enrollmentCount()).isEqualTo(((Number) counts[0]).longValue());
            assertThat(row.completedCount()).isEqualTo(((Number) counts[1]).longValue());
        }
    }
    @Test void recentListsUseActualTimestampSortAndLimits() {
        var data = service.get();
        assertThat(data.recentCompletions()).extracting(c -> c.enrollmentId()).containsExactlyElementsOf(ids("select id from enrollments where status='COMPLETED' and completed_at is not null order by completed_at desc,id desc limit 3"));
        assertThat(data.recentAssignments()).extracting(c -> c.enrollmentId()).containsExactlyElementsOf(ids("select id from enrollments order by assigned_at desc,id desc limit 6"));
    }
    @Test void deadlineWindowMatchesInclusiveSqlAndExcludesTerminalStatuses() {
        var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        for (int days : List.of(-1, 0, 7, 8)) {
            var start = today.plusDays(days);
            long expected = ((Number) em.createNativeQuery("select count(*) from enrollments where status in ('ASSIGNED','IN_PROGRESS') and due_date between :start and :end")
                    .setParameter("start", start).setParameter("end", start.plusDays(7)).getSingleResult()).longValue();
            assertThat(repository.dueSoonCount(List.of(EnrollmentStatus.ASSIGNED, EnrollmentStatus.IN_PROGRESS), start, start.plusDays(7))).isEqualTo(expected);
        }
    }
    private long count(String sql) { return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue(); }
    @SuppressWarnings("unchecked")
    private List<Long> ids(String sql) {
        List<Number> rows = em.createNativeQuery(sql).getResultList();
        return rows.stream().map(Number::longValue).toList();
    }
}

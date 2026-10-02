package com.be.statistics;

import com.be.statistics.dto.*;
import com.be.statistics.repository.StatisticsRepository;
import com.be.statistics.service.StatisticsService;
import com.be.global.config.MemberSupportConfig;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

// Opt-in, SELECT/EXPLAIN only against the existing local MySQL. No DDL or seed mutations.
@EnabledIfSystemProperty(named="statistics.local-test",matches="true")
@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=none","spring.sql.init.mode=never","spring.datasource.hikari.read-only=true"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@Import({StatisticsRepository.class,StatisticsService.class,MemberSupportConfig.class})
@Transactional(readOnly=true)
class StatisticsLocalDataTest {
    @Autowired StatisticsService service;
    @Autowired JdbcTemplate jdbc;
    @Test void actualMysqlQueriesAndIndependentTotalsMatch() {
        var today=LocalDate.now(StatisticsFilter.SEOUL);
        var filter=new StatisticsFilter(today.minusDays(365),today,null,null);
        var range=filter.resolve(Clock.systemUTC());
        var overview=service.overview(filter);
        assertThat(overview.assignedCount()).isEqualTo(jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE assigned_at>=? AND assigned_at<?",Long.class,range.startUtc(),range.endUtcExclusive()));
        assertThat(overview.completedCount()).isEqualTo(jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE status='COMPLETED' AND completed_at>=? AND completed_at<?",Long.class,range.startUtc(),range.endUtcExclusive()));
        var trends=service.trends(filter);
        assertThat(trends.points().stream().mapToLong(p->p.assignments()).sum()).isEqualTo(overview.assignedCount());
        assertThat(trends.points().stream().mapToLong(p->p.completions()).sum()).isEqualTo(overview.completedCount());
        var page=new StatisticsPageRequest(0,100);
        service.departments(filter,page).data().content().forEach(d ->
                service.departments(new StatisticsFilter(filter.startDate(),today,null,d.departmentId()),page));
        service.trends(new StatisticsFilter(today,today,null,null));
        service.trends(new StatisticsFilter(today.minusDays(29),today,null,null));
    }
    @Test void courseProjectionRequiresExistingRetrainingColumns() {
        Long columns=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='courses' AND column_name IN ('retraining_policy_id','occurrence_number')",Long.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(columns == 2, "Local DB predates existing retraining schema; not migrated by statistics tests");
        var today=LocalDate.now(StatisticsFilter.SEOUL);
        var filter=new StatisticsFilter(today.minusDays(365),today,null,null);
        service.courses(filter,new StatisticsPageRequest(0,100)).data().content().forEach(c ->
                assertThat(c.assignedCount()).isEqualTo(c.notStartedCount()+c.inProgressCount()+c.completedCount()+c.failedCount()+c.expiredCount()));
    }
    @Test void inspectExistingIndexPlansWithoutChangingSchema() {
        for(String sql : new String[]{
                "SELECT COUNT(*) FROM enrollments e JOIN members m ON m.id=e.member_id WHERE e.assigned_at>=? AND e.assigned_at<?",
                "SELECT COUNT(*),AVG(TIMESTAMPDIFF(MICROSECOND,e.assigned_at,e.completed_at)) FROM enrollments e JOIN members m ON m.id=e.member_id WHERE e.status='COMPLETED' AND e.completed_at>=? AND e.completed_at<?",
                "SELECT e.course_id,COUNT(*) FROM enrollments e JOIN members m ON m.id=e.member_id WHERE e.assigned_at>=? AND e.assigned_at<? GROUP BY e.course_id",
                "SELECT e.course_id,COUNT(*) FROM exam_attempts a JOIN enrollments e ON e.id=a.enrollment_id JOIN members m ON m.id=e.member_id WHERE a.submitted_at>=? AND a.submitted_at<? GROUP BY e.course_id"}) {
            var today=LocalDate.now(StatisticsFilter.SEOUL);
            var r=new StatisticsFilter(today.minusDays(29),today,null,null).resolve(Clock.systemUTC());
            var plan=jdbc.queryForList("EXPLAIN "+sql,r.startUtc(),r.endUtcExclusive());
            assertThat(plan).isNotEmpty();
            for(var row:plan) System.out.println("Statistics EXPLAIN table="+row.get("table")+" type="+row.get("type")
                    +" key="+row.get("key")+" rows="+row.get("rows")+" extra="+row.get("Extra"));
        }
    }
}

package com.be.statistics;

import com.be.statistics.dto.*;
import com.be.statistics.dto.StatisticsResponse.*;
import com.be.statistics.repository.StatisticsRepository;
import com.be.statistics.service.StatisticsService;
import com.be.global.exception.BusinessException;
import java.time.*;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// Disposable H2 database. Fixture DDL stays in test code, not a second schema SQL file.
class StatisticsIntegrationTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), ZoneOffset.UTC);
    static final StatisticsPageRequest PAGE = new StatisticsPageRequest(null, null);
    JdbcTemplate jdbc;
    NamedParameterJdbcTemplate named;
    StatisticsService service;
    protected javax.sql.DataSource createSource() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:statistics-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return source;
    }
    protected void closeSource() { if (jdbc != null) jdbc.execute("SHUTDOWN"); }
    @BeforeEach void setup() {
        var source = createSource();
        jdbc = new JdbcTemplate(source);
        named = spy(new NamedParameterJdbcTemplate(source));
        service = new StatisticsService(new StatisticsRepository(named), CLOCK);
        jdbc.execute("CREATE TABLE departments(id BIGINT PRIMARY KEY,name VARCHAR(100),parent_department_id BIGINT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE members(id BIGINT PRIMARY KEY,department_id BIGINT,status VARCHAR(20))");
        jdbc.execute("CREATE TABLE member_roles(member_id BIGINT,role VARCHAR(20),PRIMARY KEY(member_id,role))");
        jdbc.execute("CREATE TABLE courses(id BIGINT PRIMARY KEY,title VARCHAR(200),status VARCHAR(20),retraining_policy_id BIGINT,occurrence_number INT)");
        jdbc.execute("CREATE TABLE enrollments(id BIGINT PRIMARY KEY,course_id BIGINT,member_id BIGINT,status VARCHAR(20),assigned_at DATETIME(6),completed_at DATETIME(6),UNIQUE(member_id,course_id))");
        jdbc.execute("CREATE TABLE exams(id BIGINT PRIMARY KEY,course_id BIGINT UNIQUE)");
        jdbc.execute("CREATE TABLE exam_attempts(id BIGINT PRIMARY KEY,exam_id BIGINT,enrollment_id BIGINT,submitted_at DATETIME(6),score DECIMAL(5,2),passed BOOLEAN)");
        jdbc.update("INSERT INTO departments VALUES(1,'Parent',NULL,TRUE),(2,'Child',1,TRUE),(3,'Inactive',NULL,FALSE),(4,'Empty',NULL,TRUE)");
        for (int id=1;id<=12;id++) {
            jdbc.update("INSERT INTO members VALUES(?,1,'ACTIVE')", id);
            jdbc.update("INSERT INTO member_roles VALUES(?,'EMPLOYEE')", id);
        }
        jdbc.update("INSERT INTO courses VALUES(1,'Original','CLOSED',NULL,NULL),(2,'Occurrence 1','OPEN',5,1),(3,'Occurrence 2','OPEN',5,2),(4,'No activity','OPEN',NULL,NULL),(5,'Draft','DRAFT',NULL,NULL)");
    }
    @AfterEach void cleanup() { closeSource(); }
    StatisticsFilter filter(String start, String end) { return new StatisticsFilter(LocalDate.parse(start), LocalDate.parse(end), null, null); }
    StatisticsFilter september() { return filter("2026-09-01", "2026-09-30"); }
    void enrollment(int id, int member, int course, String status, String assigned, String completed) {
        jdbc.update("INSERT INTO enrollments VALUES(?,?,?,?,?,?)", id,course,member,status,
                java.sql.Timestamp.valueOf(assigned), completed == null ? null : java.sql.Timestamp.valueOf(completed));
    }
    @Test void seoulMidnightUsesInclusiveStartAndExclusiveEndAtMicrosecondPrecision() {
        enrollment(1,1,1,"ASSIGNED","2026-08-31 14:59:59.999999",null);
        enrollment(2,2,1,"ASSIGNED","2026-08-31 15:00:00",null);
        enrollment(3,3,1,"ASSIGNED","2026-09-01 14:59:59.999999",null);
        enrollment(4,4,1,"ASSIGNED","2026-09-01 15:00:00",null);
        var f = filter("2026-09-01","2026-09-01");
        assertThat(service.overview(f).assignedCount()).isEqualTo(2);
        assertThat(service.trends(f).points()).singleElement().satisfies(p -> assertThat(p.assignments()).isEqualTo(2));
    }
    @Test void completionEventsAndCurrentCohortOutcomesUseDifferentPopulations() {
        enrollment(1,1,1,"COMPLETED","2026-08-30 15:00:00","2026-09-01 15:00:00");
        enrollment(2,2,1,"COMPLETED","2026-09-05 00:00:00","2026-10-01 00:00:00");
        enrollment(3,3,1,"FAILED","2026-09-06 00:00:00",null);
        enrollment(4,4,1,"EXPIRED","2026-09-07 00:00:00",null);
        var r = service.overview(september());
        assertThat(r.assignedCount()).isEqualTo(3);
        assertThat(r.completedCount()).isEqualTo(1);
        assertThat(r.cohortCompletedCount()).isEqualTo(1);
        assertThat(r.cohortFailedCount()).isEqualTo(1);
        assertThat(r.cohortExpiredCount()).isEqualTo(1);
        assertThat(r.completionRate()).isEqualByComparingTo("33.33");
        assertThat(r.averageCompletionDays()).isEqualByComparingTo("2.00");
        assertThat(service.trends(september()).points().stream().mapToLong(TrendPoint::completions).sum()).isEqualTo(1);
        assertThat(r.meta().timeZone()).isEqualTo("Asia/Seoul");
        assertThat(r.meta().departmentScope()).isEqualTo("DIRECT");
        assertThat(r.meta().generatedAt()).isEqualTo(CLOCK.instant());
    }
    @Test void completionBoundaryAndFractionalDurationAreExact() {
        enrollment(1,1,1,"COMPLETED","2026-08-30 15:00:00","2026-08-31 14:59:59.999999");
        enrollment(2,2,1,"COMPLETED","2026-08-30 03:00:00","2026-08-31 15:00:00");
        enrollment(3,3,1,"COMPLETED","2026-08-30 15:00:00","2026-09-01 15:00:00");
        var r = service.overview(filter("2026-09-01","2026-09-01"));
        assertThat(r.completedCount()).isEqualTo(1);
        assertThat(r.averageCompletionDays()).isEqualByComparingTo("1.50");
        assertThat(r.completionRate()).isNull();
    }
    @Test void emptyDenominatorsReturnNullAndEveryEmptyTrendDayIsPresent() {
        var r = service.overview(september());
        assertThat(r.completionRate()).isNull(); assertThat(r.averageCompletionDays()).isNull();
        var trends = service.trends(september());
        assertThat(trends.points()).hasSize(30).allSatisfy(p -> {
            assertThat(p.assignments()).isZero(); assertThat(p.completions()).isZero();
        });
    }
    @Test void courseStatusPartitionAndRetrainingOccurrencesStaySeparate() {
        String[] states = {"ASSIGNED","IN_PROGRESS","COMPLETED","FAILED","EXPIRED"};
        for (int i=0;i<5;i++) enrollment(i+1,i+1,1,states[i],"2026-09-01 00:00:00",i==2?"2026-09-02 00:00:00":null);
        enrollment(6,6,2,"ASSIGNED","2026-09-01 00:00:00",null);
        enrollment(7,6,3,"ASSIGNED","2026-09-01 00:00:00",null);
        var page = service.courses(september(),PAGE).data();
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.content()).allSatisfy(c -> assertThat(c.assignedCount()).isEqualTo(
                c.notStartedCount()+c.inProgressCount()+c.completedCount()+c.failedCount()+c.expiredCount()));
        assertThat(page.content().get(0).completionRate()).isEqualByComparingTo("20.00");
        assertThat(page.content().get(1).occurrenceNumber()).isEqualTo(1);
        assertThat(page.content().get(2).occurrenceNumber()).isEqualTo(2);
        assertThat(page.content().get(1).retrainingPolicyId()).isEqualTo(5);
        assertThat(page.content().get(3).completionRate()).isNull();
    }
    @Test void departmentsUseCurrentDirectMembershipIncludingInactiveAndResignedPerformance() {
        jdbc.update("UPDATE members SET department_id=2 WHERE id=2");
        jdbc.update("UPDATE members SET department_id=3,status='RESIGNED' WHERE id=3");
        jdbc.update("UPDATE members SET status='ON_LEAVE' WHERE id=1");
        jdbc.update("INSERT INTO member_roles VALUES(1,'ADMIN')");
        jdbc.update("DELETE FROM member_roles WHERE member_id=4");
        jdbc.update("INSERT INTO member_roles VALUES(4,'INSTRUCTOR')");
        enrollment(1,1,1,"COMPLETED","2026-09-01 00:00:00","2026-09-02 00:00:00");
        enrollment(2,2,1,"FAILED","2026-09-01 00:00:00",null);
        enrollment(3,3,1,"EXPIRED","2026-09-01 00:00:00",null);
        var rows = service.departments(september(),PAGE).data().content();
        assertThat(rows.get(0).assignedCount()).isEqualTo(1);
        assertThat(rows.get(0).currentEmployeeCount()).isEqualTo(9);
        assertThat(rows.get(1).parentDepartmentId()).isEqualTo(1);
        assertThat(rows.get(1).failedCount()).isEqualTo(1);
        assertThat(rows.get(2).active()).isFalse();
        assertThat(rows.get(2).currentEmployeeCount()).isZero();
        assertThat(rows.get(2).expiredCount()).isEqualTo(1);
        jdbc.update("UPDATE members SET department_id=2 WHERE id=1");
        var after = service.departments(september(),PAGE).data().content();
        assertThat(after.get(0).assignedCount()).isZero();
        assertThat(after.get(1).assignedCount()).isEqualTo(2);
        var f = new StatisticsFilter(september().startDate(),september().endDate(),1L,1L);
        assertThat(service.overview(f).assignedCount()).isZero();
        assertThat(service.departments(f,PAGE).data().content()).hasSize(1);
        assertThat(service.courses(f,PAGE).data().content()).singleElement().satisfies(c -> assertThat(c.assignedCount()).isZero());
    }
    @Test void examRetriesNeverMultiplyEnrollmentAndUseSubmittedDateIndependently() {
        enrollment(1,1,1,"IN_PROGRESS","2026-09-01 00:00:00",null);
        enrollment(2,2,1,"COMPLETED","2026-08-01 00:00:00","2026-09-02 00:00:00");
        jdbc.update("INSERT INTO exams VALUES(1,1),(2,2)");
        jdbc.update("INSERT INTO exam_attempts VALUES(1,1,1,'2026-09-01 00:00:00',40,FALSE),(2,1,1,'2026-09-02 00:00:00',80,TRUE),(3,1,1,NULL,NULL,NULL),(4,1,2,'2026-09-03 00:00:00',90,TRUE),(5,1,1,'2026-09-30 15:00:00',10,FALSE)");
        var rows = service.courses(september(),PAGE).data().content();
        assertThat(rows.get(0).assignedCount()).isEqualTo(1);
        assertThat(rows.get(0).exam().submittedAttemptCount()).isEqualTo(3);
        assertThat(rows.get(0).exam().passedAttemptCount()).isEqualTo(2);
        assertThat(rows.get(0).exam().averageScore()).isEqualByComparingTo("70.00");
        assertThat(rows.get(0).exam().attemptPassRate()).isEqualByComparingTo("66.67");
        assertThat(rows.get(1).exam().submittedAttemptCount()).isZero();
        assertThat(rows.get(1).exam().averageScore()).isNull();
        assertThat(rows.get(1).exam().attemptPassRate()).isNull();
        assertThat(rows.get(2).exam()).isNull();
        jdbc.update("UPDATE members SET department_id=2 WHERE id=2");
        var f = new StatisticsFilter(september().startDate(),september().endDate(),1L,2L);
        var filtered = service.courses(f,PAGE).data().content().get(0);
        assertThat(filtered.assignedCount()).isZero();
        assertThat(filtered.exam().submittedAttemptCount()).isEqualTo(1);
    }
    @Test void leapDayMonthEndAndPartialMonthsAreClippedAndZeroFilled() {
        enrollment(1,1,1,"ASSIGNED","2024-01-14 14:59:59.999999",null);
        enrollment(2,2,1,"ASSIGNED","2024-01-14 15:00:00",null);
        enrollment(3,3,1,"ASSIGNED","2024-02-29 14:59:59.999999",null);
        enrollment(4,4,1,"ASSIGNED","2024-02-29 15:00:00",null);
        enrollment(5,5,1,"ASSIGNED","2024-04-01 15:00:00",null);
        var leap = service.trends(filter("2024-02-28","2024-03-01"));
        assertThat(leap.points()).hasSize(3);
        assertThat(leap.points().get(0).assignments()).isZero();
        assertThat(leap.points().get(1).assignments()).isEqualTo(1);
        assertThat(leap.points().get(2).assignments()).isEqualTo(1);
        var months = service.trends(filter("2024-01-15","2024-04-01"));
        assertThat(months.granularity()).isEqualTo(Granularity.MONTH);
        assertThat(months.points()).hasSize(4);
        assertThat(months.points().get(0).periodStart()).isEqualTo(LocalDate.of(2024,1,15));
        assertThat(months.points().get(0).assignments()).isEqualTo(1);
        assertThat(months.points().get(1).assignments()).isEqualTo(1);
        assertThat(months.points().get(2).assignments()).isEqualTo(1);
        assertThat(months.points().get(3).assignments()).isZero();
        assertThat(months.points().get(3).periodEnd()).isEqualTo(LocalDate.of(2024,4,1));
    }
    @Test void granularitySwitchesAt62And63DaysAnd366IsAllowed() {
        assertThat(service.trends(filter("2024-01-01","2024-03-02")).granularity()).isEqualTo(Granularity.DAY);
        assertThat(service.trends(filter("2024-01-01","2024-03-03")).granularity()).isEqualTo(Granularity.MONTH);
        assertThat(filter("2024-01-01","2024-12-31").resolve(CLOCK).days()).isEqualTo(366);
    }
    @Test void courseAndDepartmentFiltersApplyToBothEventSeriesAndAverages() {
        jdbc.update("UPDATE members SET department_id=2 WHERE id=2");
        enrollment(1,1,1,"COMPLETED","2026-09-01 00:00:00","2026-09-03 00:00:00");
        enrollment(2,2,1,"COMPLETED","2026-09-01 00:00:00","2026-09-10 00:00:00");
        enrollment(3,1,2,"COMPLETED","2026-09-01 00:00:00","2026-09-20 00:00:00");
        var f = new StatisticsFilter(september().startDate(),september().endDate(),1L,1L);
        var summary = service.overview(f);
        assertThat(summary.assignedCount()).isEqualTo(1);
        assertThat(summary.completedCount()).isEqualTo(1);
        assertThat(summary.averageCompletionDays()).isEqualByComparingTo("2.00");
        var trends = service.trends(f);
        assertThat(trends.points().stream().mapToLong(TrendPoint::assignments).sum()).isEqualTo(1);
        assertThat(trends.points().stream().mapToLong(TrendPoint::completions).sum()).isEqualTo(1);
        assertThat(service.departments(f,PAGE).data().content()).singleElement()
                .satisfies(d -> assertThat(d.assignedCount()).isEqualTo(1));
    }
    @Test void todayValidationUsesSeoulRatherThanUtcCalendarDate() {
        var boundary = Clock.fixed(Instant.parse("2026-10-01T15:00:00Z"),ZoneOffset.UTC);
        var r = filter("2026-10-02","2026-10-02").resolve(boundary);
        assertThat(r.startUtc()).isEqualTo(LocalDateTime.parse("2026-10-01T15:00:00"));
        assertThat(r.endUtcExclusive()).isEqualTo(LocalDateTime.parse("2026-10-02T15:00:00"));
    }
    @ParameterizedTest @CsvSource({"2026-09-02,2026-09-01", "2024-01-01,2025-01-01", "2026-10-02,2026-10-03", "0999-01-01,0999-01-02"})
    void invalidRangesFailBeforeQuery(String start, String end) {
        clearInvocations(named);
        assertThatThrownBy(() -> service.overview(filter(start,end))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(named);
    }
    @Test void requiredDatesAndPositiveIdsAreValidatedInService() {
        for(var f : List.of(new StatisticsFilter(null,LocalDate.now(CLOCK),null,null),
                new StatisticsFilter(LocalDate.now(CLOCK),null,null,null),
                new StatisticsFilter(september().startDate(),september().endDate(),0L,null)))
            assertThatThrownBy(() -> service.overview(f)).isInstanceOf(BusinessException.class);
    }
    @Test void pagesAreStableAndExamQueriesAreBatchedNotPerCourse() {
        clearInvocations(named);
        var large = service.courses(september(),PAGE);
        verify(named,times(1)).query(contains("FROM exam_attempts"),
                any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), any(org.springframework.jdbc.core.RowCallbackHandler.class));
        int queries = mockingDetails(named).getInvocations().size();
        clearInvocations(named);
        var small = service.courses(september(),new StatisticsPageRequest(1,1));
        assertThat(mockingDetails(named).getInvocations()).hasSize(queries);
        assertThat(large.data().content()).hasSize(4);
        assertThat(small.data().content()).singleElement().satisfies(c -> assertThat(c.courseId()).isEqualTo(2));
        assertThat(small.data().totalPages()).isEqualTo(4);
        assertThat(service.courses(september(),new StatisticsPageRequest(10,1)).data().content()).isEmpty();
        assertThat(service.departments(september(),new StatisticsPageRequest(1,1)).data().content())
                .singleElement().satisfies(d -> assertThat(d.departmentId()).isEqualTo(2));
    }
}

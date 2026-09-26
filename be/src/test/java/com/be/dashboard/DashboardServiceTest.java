package com.be.dashboard;

import com.be.dashboard.repository.DashboardRepository;
import com.be.dashboard.service.DashboardService;
import com.be.course.enums.*;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.member.enums.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    final DashboardRepository repository = mock(DashboardRepository.class);
    final DashboardService service = new DashboardService(repository, Clock.fixed(Instant.parse("2026-09-26T15:00:00Z"), ZoneOffset.UTC));

    @Test void countsEmployeesOpenCoursesAllStatusesAndRatesWithoutPerRowQueries() {
        var statusCounts = List.of(count(EnrollmentStatus.ASSIGNED, 2), count(EnrollmentStatus.IN_PROGRESS, 3),
                count(EnrollmentStatus.COMPLETED, 4), count(EnrollmentStatus.FAILED, 2), count(EnrollmentStatus.EXPIRED, 1));
        when(repository.employeeCount(Role.EMPLOYEE, List.of(MemberStatus.ACTIVE, MemberStatus.ON_LEAVE))).thenReturn(17L);
        when(repository.courseCount(CourseStatus.OPEN)).thenReturn(8L);
        when(repository.statusCounts()).thenReturn(statusCounts);
        var activeCourses = List.of(course(1, 3, 1), course(2, 0, 0));
        when(repository.activeCourses(PageRequest.of(0, 4))).thenReturn(activeCourses);
        var result = service.get();
        assertThat(result.overview().employeeCount()).isEqualTo(17);
        assertThat(result.overview().openCourseCount()).isEqualTo(8);
        assertThat(result.overview().ongoingEnrollmentCount()).isEqualTo(5);
        assertThat(result.overview().completionRate()).isEqualByComparingTo("33.33");
        assertThat(result.distribution().total()).isEqualTo(12);
        assertThat(result.distribution().counts()).extracting(c -> c.count()).containsExactly(2L, 3L, 4L, 2L, 1L);
        assertThat(result.attention().failedCount()).isEqualTo(2);
        assertThat(result.attention().expiredCount()).isEqualTo(1);
        assertThat(result.activeCourses().getFirst().completionRate()).isEqualByComparingTo("33.33");
        assertThat(result.activeCourses().getLast().completionRate()).isZero();
        verify(repository).activeCourses(PageRequest.of(0, 4));
        assertThat(mockingDetails(repository).getInvocations()).hasSize(7);
    }
    @Test void noEnrollmentsReturnsZeroAndAllFiveStatuses() {
        var result = service.get();
        assertThat(result.overview().completionRate()).isZero();
        assertThat(result.distribution().total()).isZero();
        assertThat(result.distribution().counts()).hasSize(5).allSatisfy(c -> assertThat(c.count()).isZero());
        assertThat(result.activeCourses()).isEmpty();
        assertThat(result.recentCompletions()).isEmpty();
        assertThat(result.recentAssignments()).isEmpty();
    }
    @ParameterizedTest
    @CsvSource({"2026-09-26T14:59:59Z,2026-09-26,2026-10-03", "2026-09-26T15:00:00Z,2026-09-27,2026-10-04"})
    void dueSoonUsesSeoulMidnightAndSevenDaysInclusive(String instant, LocalDate today, LocalDate through) {
        when(repository.dueSoonCount(List.of(EnrollmentStatus.ASSIGNED, EnrollmentStatus.IN_PROGRESS), today, through)).thenReturn(6L);
        var tested = new DashboardService(repository, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        assertThat(tested.get().attention().dueSoonCount()).isEqualTo(6);
        verify(repository).dueSoonCount(List.of(EnrollmentStatus.ASSIGNED, EnrollmentStatus.IN_PROGRESS), today, through);
    }
    @Test void recentListsKeepRepositoryOrderAndBoundedPageSizes() {
        var newer = recent(9L, LocalDateTime.of(2026, 9, 26, 8, 0));
        var older = recent(8L, LocalDateTime.of(2026, 9, 25, 8, 0));
        when(repository.recentCompletions(PageRequest.of(0, 3))).thenReturn(List.of(newer, older));
        when(repository.recentAssignments(PageRequest.of(0, 6))).thenReturn(List.of(newer));
        var result = service.get();
        assertThat(result.recentCompletions()).extracting(c -> c.enrollmentId()).containsExactly(9L, 8L);
        assertThat(result.recentCompletions().getFirst().completedAt()).isEqualTo(newer.getCompletedAt());
        assertThat(result.recentAssignments().getFirst().assignedAt()).isEqualTo(newer.getAssignedAt());
    }
    private DashboardRepository.StatusCount count(EnrollmentStatus status, long count) {
        var row = mock(DashboardRepository.StatusCount.class);
        when(row.getStatus()).thenReturn(status); when(row.getCount()).thenReturn(count); return row;
    }
    private DashboardRepository.CourseCount course(long id, long total, long completed) {
        var row = mock(DashboardRepository.CourseCount.class);
        when(row.getCourseId()).thenReturn(id); when(row.getTitle()).thenReturn("실제 과정"); when(row.getType()).thenReturn(CourseType.MANDATORY);
        when(row.getEnrollmentCount()).thenReturn(total); when(row.getCompletedCount()).thenReturn(completed); return row;
    }
    private DashboardRepository.RecentEnrollment recent(long id, LocalDateTime date) {
        var row = mock(DashboardRepository.RecentEnrollment.class);
        when(row.getEnrollmentId()).thenReturn(id); when(row.getMemberName()).thenReturn("직원"); when(row.getCourseTitle()).thenReturn("교육");
        when(row.getCompletedAt()).thenReturn(date); when(row.getAssignedAt()).thenReturn(date.minusDays(1)); return row;
    }
}

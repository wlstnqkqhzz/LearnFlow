package com.be.enrollment;

import com.be.course.entity.Course;
import com.be.course.enums.*;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.enrollment.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static com.be.assignment.AssignmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 엔티티·Spring 트랜잭션 프록시와 모의 저장소/트랜잭션 관리자로 정책 및 격리 경계 검증
class EnrollmentExpirationServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime ASSIGNED_AT = LocalDateTime.of(2026, 9, 19, 0, 0);
    private static final List<EnrollmentStatus> TARGETS = List.of(EnrollmentStatus.ASSIGNED, EnrollmentStatus.IN_PROGRESS);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private AnnotationConfigApplicationContext context;
    private EnrollmentExpirationProcessor processor;
    private EnrollmentExpirationService service;

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfig {}

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfig.class);
        context.registerBean(EnrollmentRepository.class, () -> enrollments);
        context.registerBean(PlatformTransactionManager.class, () -> transactions);
        context.registerBean(EnrollmentExpirationProcessor.class);
        context.refresh();
        processor = context.getBean(EnrollmentExpirationProcessor.class);
        service = service(CLOCK);
    }

    @AfterEach
    void tearDown() { context.close(); }

    @ParameterizedTest
    @CsvSource({"2026-09-21,false", "2026-09-20,true", "2026-09-19,true", "2026-09-22,false"})
    void dueDateBoundaryIsStrictlyBeforeToday(LocalDate due, boolean expired) {
        var enrollment = enrollment(1L, due, EnrollmentStatus.ASSIGNED, CourseStatus.OPEN);
        candidate(enrollment, TODAY);
        var result = service.expireOverdueEnrollments();
        assertThat(result.expiredCount()).isEqualTo(expired ? 1 : 0);
        assertThat(enrollment.getStatus()).isEqualTo(expired ? EnrollmentStatus.EXPIRED : EnrollmentStatus.ASSIGNED);
        verify(enrollments, times(expired ? 1 : 0)).flush();
    }

    @ParameterizedTest
    @EnumSource(EnrollmentStatus.class)
    void onlyAssignedAndInProgressExpireAndAllTimestampsArePreserved(EnrollmentStatus status) {
        var enrollment = enrollment(1L, TODAY.minusDays(1), status, CourseStatus.OPEN);
        var started = enrollment.getStartedAt();
        var completed = enrollment.getCompletedAt();
        candidate(enrollment, TODAY);
        boolean eligible = status == EnrollmentStatus.ASSIGNED || status == EnrollmentStatus.IN_PROGRESS;
        assertThat(service.expireOverdueEnrollments().expiredCount()).isEqualTo(eligible ? 1 : 0);
        assertThat(enrollment.getStatus()).isEqualTo(eligible ? EnrollmentStatus.EXPIRED : status);
        assertThat(enrollment.getAssignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(enrollment.getStartedAt()).isEqualTo(started);
        assertThat(enrollment.getCompletedAt()).isEqualTo(completed);
        assertThat(enrollment.getDueDate()).isEqualTo(TODAY.minusDays(1));
        if (eligible) assertThat(enrollment.getCompletedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(CourseStatus.class)
    void courseStatusDoesNotRestrictExpiration(CourseStatus status) {
        var enrollment = enrollment(1L, TODAY.minusDays(1), EnrollmentStatus.IN_PROGRESS, status);
        candidate(enrollment, TODAY);
        assertThat(service.expireOverdueEnrollments().expiredCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Los_Angeles", "Asia/Tokyo"})
    void usesSeoulDateRegardlessOfInjectedClockZone(String zone) {
        var enrollment = enrollment(1L, TODAY.minusDays(1), EnrollmentStatus.ASSIGNED, CourseStatus.OPEN);
        candidate(enrollment, TODAY);
        assertThat(service(CLOCK.withZone(ZoneId.of(zone))).expireOverdueEnrollments().expiredCount()).isEqualTo(1);
        verify(enrollments).findOverdueIds(eq(TARGETS), eq(TODAY), eq(0L), any());
    }

    @Test
    void seoulMidnightChangesEligibilityWhileUtcDateStaysTheSame() {
        var enrollment = enrollment(1L, TODAY.minusDays(1), EnrollmentStatus.ASSIGNED, CourseStatus.OPEN);
        candidate(enrollment, TODAY.minusDays(1));
        assertThat(service(Clock.offset(CLOCK, Duration.ofNanos(-1))).expireOverdueEnrollments().expiredCount()).isZero();
        candidate(enrollment, TODAY);
        assertThat(service.expireOverdueEnrollments().expiredCount()).isEqualTo(1);
    }

    @Test
    void catchesUpAllPastDueDatesAfterMissedRuns() {
        var enrollment = enrollment(1L, LocalDate.of(2026, 9, 20), EnrollmentStatus.IN_PROGRESS, CourseStatus.CLOSED);
        var september23 = Clock.fixed(Instant.parse("2026-09-22T15:00:00Z"), ZoneOffset.UTC);
        candidate(enrollment, LocalDate.of(2026, 9, 23));
        assertThat(service(september23).expireOverdueEnrollments().expiredCount()).isEqualTo(1);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
    }

    @Test
    void repeatedRunsAreIdempotentEvenIfCandidateIsReturnedAgain() {
        var enrollment = enrollment(1L, TODAY.minusDays(1), EnrollmentStatus.IN_PROGRESS, CourseStatus.OPEN);
        candidate(enrollment, TODAY);
        assertThat(service.expireOverdueEnrollments().expiredCount()).isEqualTo(1);
        assertThat(service.expireOverdueEnrollments().expiredCount()).isZero();
        verify(enrollments, times(1)).flush();
    }

    @Test
    void rechecksTerminalStateReachedAfterCandidateQuery() {
        var enrollment = enrollment(1L, TODAY.minusDays(1), EnrollmentStatus.IN_PROGRESS, CourseStatus.OPEN);
        candidate(enrollment, TODAY);
        when(enrollments.findById(1L)).thenAnswer(call -> {
            enrollment.completeLearning(ASSIGNED_AT.plusHours(2));
            return Optional.of(enrollment);
        });
        assertThat(service.expireOverdueEnrollments().expiredCount()).isZero();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        verify(enrollments, never()).flush();
    }

    @Test
    void deletedCandidateIsSkipped() {
        when(enrollments.findOverdueIds(eq(TARGETS), eq(TODAY), eq(0L), any())).thenReturn(List.of(1L));
        assertThat(service.expireOverdueEnrollments().expiredCount()).isZero();
        verify(enrollments, never()).flush();
    }

    @Test
    void scansNextIdsWithoutOffsetSkippingAfterUpdates() {
        candidate(enrollment(1L, TODAY.minusDays(2), EnrollmentStatus.ASSIGNED, CourseStatus.OPEN), TODAY);
        when(enrollments.findOverdueIds(eq(TARGETS), eq(TODAY), eq(1L), any())).thenReturn(List.of(8L));
        when(enrollments.findById(8L)).thenReturn(Optional.of(
                enrollment(8L, TODAY.minusDays(1), EnrollmentStatus.IN_PROGRESS, CourseStatus.OPEN)));
        assertThat(service.expireOverdueEnrollments().expiredCount()).isEqualTo(2);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(enrollments).findOverdueIds(eq(TARGETS), eq(TODAY), eq(8L), page.capture());
        assertThat(page.getValue().getOffset()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(500);
    }

    @Test
    void flushConflictRollsBackOnlyItsTransactionBeforeNextCandidate() {
        twoCandidates();
        doThrow(new OptimisticLockingFailureException("concurrent completion")).doNothing().when(enrollments).flush();
        var result = service.expireOverdueEnrollments();
        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.conflictCount()).isEqualTo(1);
        var order = inOrder(transactions, enrollments);
        order.verify(transactions).getTransaction(any());
        order.verify(enrollments).findById(1L);
        order.verify(enrollments).flush();
        order.verify(transactions).rollback(any());
        order.verify(transactions).getTransaction(any());
        order.verify(enrollments).findById(2L);
        order.verify(enrollments).flush();
        order.verify(transactions).commit(any());
        var definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).allSatisfy(definition ->
                assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(enrollments, times(1)).findById(1L); // 동일 실행 내 충돌 재시도 없음
    }

    @Test
    void commitConflictIsNotCountedAsSuccessAndNextCandidateStillRuns() {
        twoCandidates();
        doThrow(new OptimisticLockingFailureException("commit conflict")).doNothing().when(transactions).commit(any());
        var result = service.expireOverdueEnrollments();
        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.conflictCount()).isEqualTo(1);
        verify(transactions, times(2)).commit(any());
    }

    @Test
    void unexpectedFailureIsNotSilentlySwallowed() {
        twoCandidates();
        var failure = new IllegalStateException("database unavailable");
        doThrow(failure).when(enrollments).flush();
        assertThatThrownBy(service::expireOverdueEnrollments).isSameAs(failure);
        verify(transactions).rollback(any());
        verify(enrollments, never()).findById(2L);
    }

    private EnrollmentExpirationService service(Clock clock) {
        return new EnrollmentExpirationService(enrollments, processor, clock);
    }

    private void candidate(Enrollment enrollment, LocalDate today) {
        when(enrollments.findOverdueIds(eq(TARGETS), eq(today), eq(0L), any())).thenReturn(List.of(enrollment.getId()));
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));
    }

    private void twoCandidates() {
        when(enrollments.findOverdueIds(eq(TARGETS), eq(TODAY), eq(0L), any())).thenReturn(List.of(1L, 2L));
        for (long id : List.of(1L, 2L)) when(enrollments.findById(id)).thenReturn(Optional.of(
                enrollment(id, TODAY.minusDays(1), EnrollmentStatus.IN_PROGRESS, CourseStatus.OPEN)));
    }

    private Enrollment enrollment(long id, LocalDate due, EnrollmentStatus status, CourseStatus courseStatus) {
        var course = Course.create("교육", null, CourseType.MANDATORY, ASSIGNED_AT.toLocalDate(), due, BigDecimal.TEN, null);
        course.changeStatus(courseStatus);
        var enrollment = Enrollment.manual(member(id), course, ASSIGNED_AT);
        id(enrollment, id);
        if (status != EnrollmentStatus.ASSIGNED && status != EnrollmentStatus.EXPIRED) enrollment.startLearning(ASSIGNED_AT.plusHours(1));
        if (status == EnrollmentStatus.COMPLETED) enrollment.completeLearning(ASSIGNED_AT.plusHours(2));
        if (status == EnrollmentStatus.FAILED) enrollment.failLearning();
        if (status == EnrollmentStatus.EXPIRED) enrollment.expireIfOverdue(TODAY);
        return enrollment;
    }
}

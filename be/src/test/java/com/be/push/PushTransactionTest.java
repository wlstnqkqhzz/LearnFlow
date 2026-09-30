package com.be.push;

import com.be.enrollment.repository.EnrollmentRepository;
import com.be.notification.NotificationCreated;
import com.be.notification.enums.NotificationType;
import com.be.notification.service.NotificationService;
import com.be.security.JwtTestSupport;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.be.push.PushTestSupport.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.jpa.show-sql=false", "spring.sql.init.mode=never"})
@Import({PushTransactionTest.Config.class, NotificationService.class, PushDispatcher.class,
        PushDeliveryStore.class, PushSubscriptionService.class, PushEndpointPolicy.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PushTransactionTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false) static class Config {
        @Bean java.time.Clock clock() { return CLOCK; }
        @Bean PushProperties properties() { return ENABLED; }
        @Bean Tasks tasks() { return new Tasks(); }
    }
    @Autowired NotificationService notifications;
    @Autowired PushSubscriptionService subscriptions;
    @Autowired PushSubscriptionRepository repository;
    @Autowired PushDeliveryStore store;
    @Autowired EnrollmentRepository enrollments;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired Tasks tasks;
    @Autowired ApplicationEventPublisher events;
    @MockitoBean PushTransport transport;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("jwt.secret", JwtTestSupport::secret); r.add("jwt.access-token-ttl-seconds", () -> 300);
        r.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }
    @BeforeEach void resetTasks() { tasks.immediate.clear(); tasks.retries.clear(); tasks.delays.clear(); tasks.accept = true; }
    @AfterEach void cleanup() {
        jdbc.execute("DROP TABLE push_subscriptions"); jdbc.execute("DROP TABLE notifications");
        jdbc.execute("DROP TABLE enrollments"); jdbc.execute("DROP TABLE members"); jdbc.execute("DROP TABLE courses"); jdbc.execute("DROP TABLE assignment_rules");
    }
    void business(boolean rollback) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var enrollment = enrollments.findById(3L).orElseThrow();
            enrollment.completeLearning(java.time.LocalDateTime.of(2026, 9, 30, 0, 0));
            notifications.notify(enrollment, NotificationType.COURSE_COMPLETED);
            assertThat(tasks.immediate).isEmpty();
            verifyNoInteractions(transport);
            if (rollback) tx.setRollbackOnly();
        });
    }
    @Test void commitEnqueuesOnlyAfterCommitAndTransportSeesNoTransaction() throws Exception {
        subscriptions.register(2L, request("device"));
        when(transport.send(any(), any())).thenAnswer(c -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new PushTransport.Result(201, null);
        });
        business(false); assertThat(tasks.immediate).hasSize(1);
        tasks.run(); verify(transport).send(any(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isEqualTo(1);
    }
    @Test void rollbackRemovesBusinessAndNotificationAndNeverEnqueues() {
        subscriptions.register(2L, request("device")); business(true);
        assertThat(tasks.immediate).isEmpty(); verifyNoInteractions(transport);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM enrollments WHERE id=3", String.class)).isEqualTo("IN_PROGRESS");
    }
    @Test void pushFailureDoesNotUndoBusinessOrDbNotification() throws Exception {
        subscriptions.register(2L, request("device"));
        when(transport.send(any(), any())).thenThrow(new IOException("network"));
        business(false); tasks.run(); tasks.retryAll();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM enrollments WHERE id=3", String.class)).isEqualTo("COMPLETED");
        verify(transport, times(3)).send(any(), any());
    }
    @Test void executorSaturationDoesNotUndoCommit() {
        tasks.accept = false; business(false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM enrollments WHERE id=3", String.class)).isEqualTo("COMPLETED");
    }
    @Test void eventWithoutTransactionDoesNotDeliver() {
        events.publishEvent(new NotificationCreated(1L)); assertThat(tasks.immediate).isEmpty();
    }
    @Test void resignedMemberExcludedEvenAfterEnqueue() {
        subscriptions.register(2L, request("device")); business(false);
        jdbc.update("UPDATE members SET status='RESIGNED' WHERE id=2");
        tasks.run(); verifyNoInteractions(transport);
    }
    @Test void registrationIdempotencyMultipleDevicesOwnershipAndDisable() {
        var request = request("device");
        var first = subscriptions.register(2L, request);
        var duplicate = subscriptions.register(2L, request);
        var other = subscriptions.register(2L, request("other-device"));
        assertThat(first.subscriptionId()).isEqualTo(duplicate.subscriptionId()).isNotEqualTo(other.subscriptionId());
        assertThat(repository.count()).isEqualTo(2);
        assertThatThrownBy(() -> subscriptions.register(3L, request)).isInstanceOf(com.be.global.exception.BusinessException.class);
        assertThatThrownBy(() -> subscriptions.get(3L, first.subscriptionId())).isInstanceOf(com.be.global.exception.BusinessException.class);
        assertThatThrownBy(() -> subscriptions.disable(3L, first.subscriptionId())).isInstanceOf(com.be.global.exception.BusinessException.class);
        subscriptions.disable(2L, first.subscriptionId()); subscriptions.disable(2L, first.subscriptionId());
        assertThat(subscriptions.get(2L, first.subscriptionId()).enabled()).isFalse();
        assertThat(subscriptions.get(2L, other.subscriptionId()).enabled()).isTrue();
        assertThat(subscriptions.register(2L, request).subscriptionId()).isEqualTo(first.subscriptionId());
    }
    @Test void limitAllowsIdempotentUpdateAndFreedSlot() {
        var original = request("original"); var first = subscriptions.register(2L, original);
        for (int i = 0; i < 9; i++) subscriptions.register(2L, request("device-" + i));
        assertThatThrownBy(() -> subscriptions.register(2L, request("excess"))).isInstanceOf(com.be.global.exception.BusinessException.class);
        assertThat(subscriptions.register(2L, original).enabled()).isTrue();
        subscriptions.disable(2L, first.subscriptionId());
        assertThat(subscriptions.register(2L, request("replacement")).enabled()).isTrue();
    }
    @Test void stale410CannotDisableRefreshedKeys() {
        var request = request("device"); var s = subscriptions.register(2L, request); business(false);
        Long notificationId = jdbc.queryForObject("SELECT id FROM notifications", Long.class);
        var old = store.load(notificationId, s.subscriptionId()).orElseThrow();
        subscriptions.register(2L, request("device")); // new key forces version change, even with fixed clock
        store.disable(old);
        assertThat(subscriptions.get(2L, s.subscriptionId()).enabled()).isTrue();
        store.disable(store.load(notificationId, s.subscriptionId()).orElseThrow());
        assertThat(subscriptions.get(2L, s.subscriptionId()).enabled()).isFalse();
    }
    @Test void expiredSubscriptionExcluded() {
        var s = subscriptions.register(2L, request("device")); business(false);
        jdbc.update("UPDATE push_subscriptions SET expiration_time=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?", s.subscriptionId());
        tasks.run(); verifyNoInteractions(transport);
    }
    @Test void concurrentSameMemberRegistrationRemainsOneRow() throws Exception {
        var request = request("race");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Long> registration = () -> { start.await(); return subscriptions.register(2L, request).subscriptionId(); };
            var a = executor.submit(registration); var b = executor.submit(registration); start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
            assertThat(repository.count()).isEqualTo(1);
        }
    }
}

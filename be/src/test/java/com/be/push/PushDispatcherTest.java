package com.be.push;

import com.be.notification.NotificationCreated;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.be.push.PushTestSupport.*;

@ExtendWith(OutputCaptureExtension.class)
class PushDispatcherTest {
    PushDeliveryStore store = mock(PushDeliveryStore.class);
    PushTransport transport = mock(PushTransport.class);
    Tasks tasks = new Tasks();
    PushDispatcher dispatcher = new PushDispatcher(ENABLED, tasks, store, transport, CLOCK);
    PushDeliveryStore.Target target = new PushDeliveryStore.Target(2L, 0, "SECRET_ENDPOINT", "SECRET_KEY", "SECRET_AUTH");
    @BeforeEach void setup() {
        when(store.targets(1L)).thenReturn(List.of(2L));
        when(store.load(1L, 2L)).thenReturn(Optional.of(target));
    }
    void start() { dispatcher.onCreated(new NotificationCreated(1L)); tasks.run(); }
    @ParameterizedTest @ValueSource(ints = {200, 201, 202, 400, 401, 403, 413, 302})
    void terminalStatusesDoNotRetryOrDisable(int status) throws Exception {
        when(transport.send(target, 1L)).thenReturn(new PushTransport.Result(status, null));
        start(); tasks.retryAll();
        verify(transport).send(target, 1L); verify(store, never()).disable(any());
    }
    @ParameterizedTest @ValueSource(ints = {404, 410})
    void invalidSubscriptionDisabled(int status) throws Exception {
        when(transport.send(target, 1L)).thenReturn(new PushTransport.Result(status, null));
        start(); verify(store).disable(target); assertThat(tasks.retries).isEmpty();
    }
    @Test void serverErrorRetriesAtMostTwice() throws Exception {
        when(transport.send(target, 1L)).thenReturn(new PushTransport.Result(503, null));
        start(); tasks.retryAll(); verify(transport, times(3)).send(target, 1L);
        assertThat(tasks.delays).hasSize(2);
        assertThat(tasks.delays.get(0)).isBetween(Duration.ofSeconds(30), Duration.ofSeconds(31));
        assertThat(tasks.delays.get(1)).isBetween(Duration.ofSeconds(120), Duration.ofSeconds(121));
    }
    @Test void networkRetryAndSensitiveExceptionsAreNotLogged(CapturedOutput output) throws Exception {
        when(transport.send(target, 1L)).thenThrow(new IOException("SECRET_ENDPOINT SECRET_KEY SECRET_AUTH Authorization"));
        start(); tasks.retryAll(); verify(transport, times(3)).send(target, 1L);
        assertThat(output.getAll()).doesNotContain("SECRET_ENDPOINT", "SECRET_KEY", "SECRET_AUTH", "Authorization");
    }
    @Test void oneFailureDoesNotBlockOtherDevice(CapturedOutput output) throws Exception {
        var other = new PushDeliveryStore.Target(3L, 0, "other", "key", "auth");
        when(store.targets(1L)).thenReturn(List.of(2L, 3L));
        when(store.load(1L, 3L)).thenReturn(Optional.of(other));
        when(transport.send(target, 1L)).thenThrow(new IllegalArgumentException("SECRET_KEY"));
        when(transport.send(other, 1L)).thenReturn(new PushTransport.Result(201, null));
        start(); verify(transport).send(other, 1L);
        assertThat(output.getAll()).doesNotContain("SECRET_KEY");
    }
    @Test void retryRechecksEligibility() throws Exception {
        when(transport.send(target, 1L)).thenReturn(new PushTransport.Result(500, null));
        start(); when(store.load(1L, 2L)).thenReturn(Optional.empty()); tasks.retryAll();
        verify(transport).send(target, 1L);
    }
    @Test void retryAfterWithinDeadlineIsRespected() throws Exception {
        when(transport.send(target, 1L)).thenReturn(new PushTransport.Result(429, "180"));
        start(); assertThat(tasks.delays).containsExactly(Duration.ofSeconds(180));
    }
    @Test void retryAfterBeyondDeadlineStops() throws Exception {
        when(transport.send(target, 1L)).thenReturn(new PushTransport.Result(429, "999999999999999999999"));
        start(); assertThat(tasks.retries).isEmpty();
    }
    @Test void retryAfterHttpDate() {
        assertThat(PushDispatcher.retryDelay("Wed, 30 Sep 2026 00:03:00 GMT", CLOCK.instant())).isEqualTo(Duration.ofMinutes(3));
    }
    @Test void rejectionCannotEscapeListener() {
        tasks.accept = false;
        assertThatCode(() -> dispatcher.onCreated(new NotificationCreated(1L))).doesNotThrowAnyException();
        verifyNoInteractions(store, transport);
    }
    @Test void disabledPushDoesNotQueryOrSubmit() {
        new PushDispatcher(new PushProperties(false, null, null, null), tasks, store, transport, CLOCK).onCreated(new NotificationCreated(1L));
        assertThat(tasks.immediate).isEmpty(); verifyNoInteractions(store, transport);
    }
    @Test void submissionExceptionCannotEscapeListener() {
        PushTasks broken = mock(PushTasks.class); when(broken.execute(any())).thenThrow(new RejectedExecutionException());
        assertThatCode(() -> new PushDispatcher(ENABLED, broken, store, transport, CLOCK).onCreated(new NotificationCreated(1L))).doesNotThrowAnyException();
    }
    @Test void realExecutorAndRetryCapacityAreBounded() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var bounded = new BoundedPushTasks(1, 1)) {
            assertThat(bounded.execute(() -> { started.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } })).isTrue();
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(bounded.execute(() -> {})).isTrue();
            assertThat(bounded.execute(() -> fail("must never execute on caller"))).isFalse();
            assertThat(bounded.schedule(() -> {}, Duration.ofMinutes(1))).isTrue();
            assertThat(bounded.schedule(() -> {}, Duration.ofMinutes(1))).isFalse();
        } finally { release.countDown(); }
    }
}

package com.be.mobilepush;

import com.be.notification.NotificationCreated;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.be.mobilepush.MobilePushTestSupport.*;

class MobilePushDispatcherTest {
    MutableClock clock; Tasks tasks; MobilePushDeliveryStore store; ExpoPushTransport transport;
    ExpoPushReceiptPoller receipts; MobilePushDispatcher dispatcher;
    final MobilePushDeliveryStore.Reference ref = new MobilePushDeliveryStore.Reference(2L, 3);
    @BeforeEach void setup() {
        clock = new MutableClock(); tasks = new Tasks(); store = mock(MobilePushDeliveryStore.class); transport = mock(ExpoPushTransport.class);
        var work = new MobilePushTasks(tasks);
        receipts = new ExpoPushReceiptPoller(ENABLED, work, transport, store, clock);
        dispatcher = new MobilePushDispatcher(ENABLED, work, store, transport, receipts, clock);
        when(store.targets(1L)).thenReturn(List.of(ref));
        when(store.load(1L, ref)).thenReturn(Optional.of(new MobilePushDeliveryStore.Target(2L, 3, "ExpoPushToken[secret]")));
    }
    void run() { dispatcher.onCreated(new NotificationCreated(1L)); tasks.run(); tasks.retryAll(); }
    @ParameterizedTest @ValueSource(ints = {429, 500, 503, 599})
    void transientHttpRetriesExactlyThreeTimes(int status) throws Exception {
        when(transport.send(any(), any())).thenReturn(new ExpoPushTransport.Result(status, null, null, null)); run();
        verify(transport, times(3)).send(any(), any()); assertThat(tasks.delays).hasSize(2);
        assertThat(tasks.delays.getFirst()).isBetween(Duration.ofSeconds(30), Duration.ofSeconds(31));
        assertThat(tasks.delays.getLast()).isBetween(Duration.ofSeconds(120), Duration.ofSeconds(121)); verify(store, never()).disable(any(), anyLong());
    }
    @Test void timeoutRetriesAndMessageRateExceededRetries() throws Exception {
        when(transport.send(any(), any())).thenThrow(new IOException()).thenReturn(new ExpoPushTransport.Result(200, null, "MessageRateExceeded", null));
        run(); verify(transport, times(3)).send(any(), any());
    }
    @ParameterizedTest @ValueSource(strings = {"MessageTooBig", "InvalidCredentials", "MismatchSenderId", "UnknownError"})
    void permanentErrorsRetainSubscription(String error) throws Exception {
        when(transport.send(any(), any())).thenReturn(new ExpoPushTransport.Result(200, null, error, null)); run();
        verify(transport).send(any(), any()); verify(store, never()).disable(any(), anyLong()); assertThat(tasks.delays).isEmpty();
    }
    @Test void payload400DoesNotDisableOrRetry() throws Exception {
        when(transport.send(any(), any())).thenReturn(new ExpoPushTransport.Result(400, null, null, null)); run();
        verify(transport).send(any(), any()); verify(store, never()).disable(any(), anyLong());
    }
    @Test void retryAfterAndDeadlineAreRespected() throws Exception {
        when(transport.send(any(), any())).thenReturn(new ExpoPushTransport.Result(429, null, null, "200"));
        dispatcher.onCreated(new NotificationCreated(1L)); tasks.run(); assertThat(tasks.delays.getFirst()).isEqualTo(Duration.ofSeconds(200));
        clock.advance(601); tasks.retryAll(); verify(transport).send(any(), any());
        assertThat(MobilePushRetry.delay(1, "99999999999999999999", clock.instant())).isEqualTo(Duration.ofDays(1));
    }
    @Test void retryAfterBeyondDeadlineIsNotScheduled() throws Exception {
        when(transport.send(any(), any())).thenReturn(new ExpoPushTransport.Result(503, null, null, "601")); run();
        verify(transport).send(any(), any()); assertThat(tasks.delays).isEmpty();
    }
    @Test void changedSubscriptionStopsPendingRetries() throws Exception {
        when(transport.send(any(), any())).thenReturn(new ExpoPushTransport.Result(500, null, null, null));
        dispatcher.onCreated(new NotificationCreated(1L)); tasks.run();
        when(store.load(1L, ref)).thenReturn(Optional.empty()); tasks.retryAll(); verify(transport).send(any(), any());
    }
    @Test void receiptBatchIsDelayedAndBounded() throws Exception {
        for (int i = 0; i < 2100; i++) receipts.accept("ticket-" + i, ref, 1L);
        assertThat(receipts.size()).isEqualTo(2000); receipts.poll(); verifyNoInteractions(transport);
        when(transport.receipts(any())).thenAnswer(c -> {
            List<String> ids = c.getArgument(0); assertThat(ids).hasSize(100);
            Map<String, String> result = new HashMap<>(); ids.forEach(id -> result.put(id, "ok"));
            return new ExpoPushTransport.Receipts(200, result, null);
        });
        clock.advance(900); receipts.poll(); assertThat(receipts.size()).isEqualTo(1900);
    }
    @Test void receiptDeviceNotRegisteredCarriesOriginalVersion() throws Exception {
        receipts.accept("t", ref, 1L); clock.advance(900);
        when(transport.receipts(any())).thenReturn(new ExpoPushTransport.Receipts(200, Map.of("t", "DeviceNotRegistered"), null));
        receipts.poll(); verify(store).disable(2L, 3); assertThat(receipts.size()).isZero();
    }
    @Test void missingOrFailedReceiptsStopAfterThreeQueriesAndNeverResend() throws Exception {
        receipts.accept("t", ref, 1L);
        when(transport.receipts(any())).thenThrow(new IOException()).thenReturn(new ExpoPushTransport.Receipts(200, Map.of(), null));
        for (int i = 0; i < 4; i++) { clock.advance(901); receipts.poll(); }
        verify(transport, times(3)).receipts(any()); verify(transport, never()).send(any(), any()); assertThat(receipts.size()).isZero();
    }
    @Test void schedulerDeduplicatesAndRecoversFromQueueFull() throws Exception {
        receipts.accept("t", ref, 1L); clock.advance(901);
        when(transport.receipts(any())).thenReturn(new ExpoPushTransport.Receipts(200, Map.of("t", "ok"), null));
        tasks.accept = false; receipts.tick(); tasks.accept = true; receipts.tick(); receipts.tick();
        assertThat(tasks.immediate).hasSize(1); tasks.run(); verify(transport).receipts(any());
    }
    @Test void actualExpoShapesAndPayloadAreMinimal() {
        var mapper = new ObjectMapper();
        assertThat(HttpExpoPushTransport.ticket(200, mapper.readTree("{\"data\":{\"status\":\"ok\",\"id\":\"ticket-id\"}}"), null).ticketId()).isEqualTo("ticket-id");
        assertThat(HttpExpoPushTransport.ticket(200, mapper.readTree("{\"data\":[{\"status\":\"error\",\"message\":\"SECRET\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}"), null).error()).isEqualTo("DeviceNotRegistered");
        assertThat(HttpExpoPushTransport.parseReceipts(200, mapper.readTree("{\"data\":{\"t\":{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}}}"), null, List.of("t", "missing")).errors()).containsExactlyEntriesOf(Map.of("t", "DeviceNotRegistered"));
        var payload = HttpExpoPushTransport.payload("token", 1L);
        assertThat(payload.get("data")).isEqualTo(Map.of("notificationId", "1"));
        assertThat(mapper.writeValueAsString(payload)).doesNotContain("enrollmentId", "route", "email", "score", "JWT");
        assertThat(token("secret", 0).toString()).doesNotContain("secret");
    }
    @Test void transportRefusesAnActiveDbTransaction() throws Exception {
        try (var http = new HttpExpoPushTransport(ENABLED, new ObjectMapper())) {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThatThrownBy(() -> http.send(new MobilePushDeliveryStore.Target(1L, 0, "token"), 1L)).isInstanceOf(IllegalStateException.class); }
            finally { org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
    }
}

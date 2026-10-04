package com.be.mobilepush;

import com.be.notification.NotificationCreated;
import java.io.IOException;
import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class MobilePushDispatcher {
    private final MobilePushProperties properties;
    private final MobilePushTasks tasks;
    private final MobilePushDeliveryStore store;
    private final ExpoPushTransport transport;
    private final ExpoPushReceiptPoller receipts;
    private final Clock clock;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onCreated(NotificationCreated event) {
        if (!properties.enabled()) return;
        try {
            Instant deadline = clock.instant().plusSeconds(600);
            if (!tasks.execute(() -> dispatch(event.notificationId(), deadline))) log.warn("Expo queue full notificationId={}", event.notificationId());
        } catch (RuntimeException e) { log.warn("Expo enqueue failed notificationId={}", event.notificationId()); }
    }
    void dispatch(Long id, Instant deadline) {
        try { for (var ref : store.targets(id)) attempt(id, ref, 1, deadline); }
        catch (RuntimeException e) { log.warn("Expo lookup failed notificationId={}", id); }
    }
    void attempt(Long id, MobilePushDeliveryStore.Reference ref, int attempt, Instant deadline) {
        if (!properties.enabled() || !clock.instant().isBefore(deadline)) return;
        try {
            var target = store.load(id, ref);
            if (target.isEmpty()) return;
            ExpoPushTransport.Result result;
            try { result = transport.send(target.get(), id); }
            catch (IOException e) { retry(id, ref, attempt, deadline, null); return; }
            if (result.status() == 200 && "DeviceNotRegistered".equals(result.error())) store.disable(ref.id(), ref.version());
            else if (MobilePushRetry.transientFailure(result.status(), result.error())) retry(id, ref, attempt, deadline, result.retryAfter());
            else if (result.status() == 200 && result.ticketId() != null) receipts.accept(result.ticketId(), ref, id);
            else log.warn("Expo rejected notificationId={} subscriptionId={} status={}", id, ref.id(), result.status());
        } catch (RuntimeException e) { log.warn("Expo delivery failed notificationId={} subscriptionId={}", id, ref.id()); }
    }
    private void retry(Long id, MobilePushDeliveryStore.Reference ref, int attempt, Instant deadline, String header) {
        if (attempt >= 3) return;
        Instant now = clock.instant();
        Duration delay = MobilePushRetry.delay(attempt, header, now);
        if (!now.plus(delay).isBefore(deadline)) return;
        if (!tasks.schedule(() -> attempt(id, ref, attempt + 1, deadline), delay)) log.warn("Expo retry queue full notificationId={}", id);
    }
}

package com.be.mobilepush;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded, best-effort memory only. Restart may lose pending receipts. No push resend on receipt loss. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpoPushReceiptPoller {
    static final int CAPACITY = 2000, BATCH = 100;
    record Pending(String ticketId, MobilePushDeliveryStore.Reference ref, Long notificationId, Instant due, int failures) {}
    private final MobilePushProperties properties;
    private final MobilePushTasks tasks;
    private final ExpoPushTransport transport;
    private final MobilePushDeliveryStore store;
    private final Clock clock;
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
    synchronized void accept(String ticketId, MobilePushDeliveryStore.Reference ref, Long id) {
        if (pending.containsKey(ticketId)) return;
        if (pending.size() >= CAPACITY) { log.warn("Expo receipt queue full notificationId={}", id); return; }
        pending.put(ticketId, new Pending(ticketId, ref, id, clock.instant().plusSeconds(900), 0));
    }
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void tick() {
        if (!properties.enabled() || !running.compareAndSet(false, true)) return;
        try { if (!tasks.execute(() -> { try { poll(); } finally { running.set(false); } })) running.set(false); }
        catch (RuntimeException e) { running.set(false); log.warn("Expo receipt enqueue failed"); }
    }
    void poll() {
        List<Pending> batch;
        synchronized (this) { batch = pending.values().stream().filter(p -> !p.due().isAfter(clock.instant())).limit(BATCH).toList(); }
        if (batch.isEmpty()) return;
        try {
            var result = transport.receipts(batch.stream().map(Pending::ticketId).toList());
            for (var p : batch) {
                String error = result.errors().get(p.ticketId());
                if (result.status() == 200 && error != null) {
                    if ("DeviceNotRegistered".equals(error)) store.disable(p.ref().id(), p.ref().version());
                    // Receipts arrive after the 10-minute send deadline. Never resend here.
                    remove(p);
                } else if (result.status() == 200 || MobilePushRetry.transientFailure(result.status(), null)) retry(p, result.retryAfter());
                else remove(p);
            }
        } catch (Exception e) { for (var p : batch) retry(p, null); log.warn("Expo receipt lookup failed count={}", batch.size()); }
    }
    private synchronized void remove(Pending p) { pending.remove(p.ticketId(), p); }
    private synchronized void retry(Pending p, String header) {
        if (pending.get(p.ticketId()) != p) return;
        if (p.failures() >= 2) { remove(p); return; }
        Duration delay = MobilePushRetry.delay(p.failures() + 1, header, clock.instant());
        if (delay.compareTo(Duration.ofHours(1)) > 0) { remove(p); return; }
        pending.put(p.ticketId(), new Pending(p.ticketId(), p.ref(), p.notificationId(), clock.instant().plus(delay), p.failures() + 1));
    }
    synchronized int size() { return pending.size(); }
}

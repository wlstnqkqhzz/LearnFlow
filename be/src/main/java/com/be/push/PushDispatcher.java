package com.be.push;

import com.be.notification.NotificationCreated;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class PushDispatcher {
    private final PushProperties properties;
    private final PushTasks tasks;
    private final PushDeliveryStore store;
    private final PushTransport transport;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onCreated(NotificationCreated event) {
        if (!properties.enabled()) return;
        try {
            Instant deadline = clock.instant().plusSeconds(600);
            if (!tasks.execute(() -> dispatch(event.notificationId(), deadline)))
                log.warn("Push capacity notificationId={}", event.notificationId());
        } catch (RuntimeException e) { log.warn("Push submit failed notificationId={}", event.notificationId()); }
    }
    void dispatch(Long notificationId, Instant deadline) {
        try {
            if (!clock.instant().isBefore(deadline)) return;
            for (Long id : store.targets(notificationId)) attempt(notificationId, id, 1, deadline);
        } catch (RuntimeException e) { log.warn("Push lookup failed notificationId={}", notificationId); }
    }
    void attempt(Long notificationId, Long subscriptionId, int attempt, Instant deadline) {
        try {
            if (!properties.enabled() || !clock.instant().isBefore(deadline)) return;
            var loaded = store.load(notificationId, subscriptionId);
            if (loaded.isEmpty()) return;
            PushTransport.Result result;
            try { result = transport.send(loaded.get(), notificationId); }
            catch (IOException e) {
                log.warn("Push network failure notificationId={} subscriptionId={} attempt={}", notificationId, subscriptionId, attempt);
                retry(notificationId, subscriptionId, attempt, deadline, null); return;
            }
            int status = result.status();
            log.info("Push notificationId={} subscriptionId={} status={} attempt={}", notificationId, subscriptionId, status, attempt);
            if (status == 404 || status == 410) store.disable(loaded.get());
            else if (status == 401 || status == 403)
                log.warn("Push configuration rejected notificationId={} subscriptionId={} status={} attempt={}", notificationId, subscriptionId, status, attempt);
            else if (status == 429 || status >= 500 && status <= 599)
                retry(notificationId, subscriptionId, attempt, deadline, status == 429 ? result.retryAfter() : null);
            // 2xx accepted; all other statuses (including redirects and 401/403) stop without deleting.
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Push failed notificationId={} subscriptionId={} attempt={}", notificationId, subscriptionId, attempt);
        }
    }
    private void retry(Long notificationId, Long subscriptionId, int attempt, Instant deadline, String retryAfter) {
        if (attempt >= 3) return;
        Instant now = clock.instant();
        Duration delay = Duration.ofSeconds(attempt == 1 ? 30 : 120).plusMillis(ThreadLocalRandom.current().nextLong(1000));
        Duration requested = retryDelay(retryAfter, now);
        if (requested != null && requested.compareTo(delay) > 0) delay = requested;
        if (delay.compareTo(Duration.between(now, deadline)) >= 0) return;
        if (!tasks.schedule(() -> attempt(notificationId, subscriptionId, attempt + 1, deadline), delay))
            log.warn("Push retry capacity notificationId={} subscriptionId={} attempt={}", notificationId, subscriptionId, attempt);
    }
    static Duration retryDelay(String header, Instant now) {
        if (header == null) return null;
        try {
            if (header.trim().matches("[0-9]+")) {
                // Bound before arithmetic, including arbitrarily large Retry-After values.
                var seconds = new java.math.BigInteger(header.trim());
                return Duration.ofSeconds(seconds.min(java.math.BigInteger.valueOf(601)).longValue());
            }
            var duration = Duration.between(now, ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            return duration.isNegative() ? Duration.ZERO : duration;
        } catch (RuntimeException e) { return null; }
    }
}

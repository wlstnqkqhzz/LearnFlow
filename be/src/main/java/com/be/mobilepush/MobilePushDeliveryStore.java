package com.be.mobilepush;

import com.be.notification.repository.NotificationRepository;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
public class MobilePushDeliveryStore {
    public record Target(Long id, long version, String token) {
        @Override public String toString() { return "Target[redacted]"; }
    }
    public record Reference(Long id, long version) {}
    private final MobilePushSubscriptionRepository subscriptions;
    private final NotificationRepository notifications;
    private final Clock clock;
    public List<Reference> targets(Long notificationId) {
        var n = notifications.findById(notificationId);
        if (n.isEmpty() || !MobilePushSubscriptionService.canReceive(n.get().getMember())) return List.of();
        return subscriptions.active(n.get().getMember().getId()).stream().map(s -> new Reference(s.getId(), s.getVersion())).toList();
    }
    public Optional<Target> load(Long notificationId, Reference ref) {
        var n = notifications.findById(notificationId);
        if (n.isEmpty() || !MobilePushSubscriptionService.canReceive(n.get().getMember())) return Optional.empty();
        return subscriptions.findByIdAndMemberId(ref.id(), n.get().getMember().getId())
                .filter(s -> s.isEnabled() && s.getVersion() == ref.version() && s.getExpoPushToken() != null)
                .map(s -> new Target(s.getId(), s.getVersion(), s.getExpoPushToken()));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disable(Long id, long version) { subscriptions.disableVersion(id, version, clock.instant()); }
}

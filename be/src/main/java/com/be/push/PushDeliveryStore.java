package com.be.push;

import com.be.member.enums.MemberStatus;
import com.be.notification.repository.NotificationRepository;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
public class PushDeliveryStore {
    private final NotificationRepository notifications;
    private final PushSubscriptionRepository subscriptions;
    private final Clock clock;
    public List<Long> targets(Long notificationId) {
        return notifications.findById(notificationId)
                .filter(n -> n.getMember().getStatus() != MemberStatus.RESIGNED)
                .map(n -> subscriptions.activeIds(n.getMember().getId(), clock.instant())).orElseGet(List::of);
    }
    // Re-read before every attempt: logout, expiration, resignation and key refresh take effect.
    public Optional<Target> load(Long notificationId, Long subscriptionId) {
        return notifications.findById(notificationId)
                .filter(n -> n.getMember().getStatus() != MemberStatus.RESIGNED)
                .flatMap(n -> subscriptions.findByIdAndMemberId(subscriptionId, n.getMember().getId()))
                .filter(s -> s.isEnabled() && (s.getExpirationTime() == null || s.getExpirationTime().isAfter(clock.instant())))
                .map(s -> new Target(s.getId(), s.getVersion(), s.getEndpoint(), s.getP256dh(), s.getAuth()));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disable(Target target) { subscriptions.disableVersion(target.id(), target.version(), clock.instant()); }
    public record Target(Long id, long version, String endpoint, String p256dh, String auth) {
        @Override public String toString() { return "PushTarget[id=" + id + ", credentials=REDACTED]"; }
    }
}

package com.be.push;

import com.be.member.entity.Member;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "push_subscriptions", uniqueConstraints = @UniqueConstraint(name = "uk_push_endpoint_hash", columnNames = "endpoint_hash"),
        indexes = @Index(name = "idx_push_member_enabled", columnList = "member_id,enabled"))
public class PushSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false) private Member member;
    @Column(nullable = false, length = 2048) private String endpoint;
    @Column(name = "endpoint_hash", nullable = false, length = 64) private String endpointHash;
    @Column(nullable = false, length = 100) private String p256dh;
    @Column(nullable = false, length = 32) private String auth;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "expiration_time") private Instant expirationTime;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    public static PushSubscription create(Member member, String hash, PushSubscriptionRequest request, Instant now) {
        var value = new PushSubscription();
        value.member = member; value.endpointHash = hash; value.createdAt = now;
        value.register(request, now);
        return value;
    }
    public void register(PushSubscriptionRequest request, Instant now) {
        endpoint = request.endpoint(); p256dh = request.p256dh(); auth = request.auth();
        expirationTime = request.expirationTime(); enabled = true; updatedAt = now;
    }
    public void disable(Instant now) { enabled = false; updatedAt = now; }
}

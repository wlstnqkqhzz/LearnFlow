package com.be.mobilepush;

import com.be.member.entity.Member;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mobile_push_subscriptions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mobile_push_installation", columnNames = "installation_id"),
        @UniqueConstraint(name = "uk_mobile_push_token", columnNames = "token_hash")},
        indexes = @Index(name = "idx_mobile_push_member_enabled", columnList = "member_id,enabled"))
public class MobilePushSubscription {
    public enum Platform { ANDROID, IOS }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false) private Member member;
    @Column(name = "installation_id", nullable = false, length = 36) private String installationId;
    @Column(name = "installation_secret_hash", nullable = false, length = 64) private String installationSecretHash;
    @Column(name = "expo_push_token", length = 512) private String expoPushToken;
    @Column(name = "token_hash", length = 64) private String tokenHash;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10) private Platform platform;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "binding_generation", nullable = false) private long bindingGeneration;
    @Version private long version;

    static MobilePushSubscription create(Member member, String installationId, String secretHash, Platform platform, Instant now) {
        var s = new MobilePushSubscription();
        s.member = member; s.installationId = installationId; s.installationSecretHash = secretHash;
        s.platform = platform; s.createdAt = now; s.updatedAt = now;
        return s;
    }
    void bind(Member next, long generation, Instant now) {
        member = next; bindingGeneration = generation; enabled = false; updatedAt = now;
    }
    void register(String token, String hash, Platform platform, Instant now) {
        if (!enabled || !token.equals(expoPushToken) || this.platform != platform) {
            expoPushToken = token; tokenHash = hash; this.platform = platform; enabled = true; updatedAt = now;
        }
    }
    void disable(Instant now) { if (enabled) { enabled = false; updatedAt = now; } }
    @Override public String toString() { return "MobilePushSubscription[redacted]"; }
}

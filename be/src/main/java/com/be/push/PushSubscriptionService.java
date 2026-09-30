package com.be.push;

import com.be.global.exception.*;
import com.be.member.repository.MemberRepository;
import com.be.member.enums.MemberStatus;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushSubscriptionService {
    private final PushSubscriptionRepository subscriptions;
    private final MemberRepository members;
    private final PushEndpointPolicy policy;
    private final PushProperties properties;
    private final Clock clock;

    @Transactional
    public PushSubscriptionResponse register(Long memberId, PushSubscriptionRequest request) {
        if (!properties.enabled()) throw new BusinessException(ErrorCode.PUSH_DISABLED);
        policy.validate(request, clock.instant());
        // Serializes same-member registrations, including count checks and duplicate requests.
        var member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus() == MemberStatus.RESIGNED)
            throw new org.springframework.security.authentication.BadCredentialsException("인증할 수 없습니다.");
        String hash = hash(request.endpoint());
        var existing = subscriptions.findByEndpointHash(hash);
        if (existing.isPresent() && (!existing.get().getMember().getId().equals(memberId)
                || !existing.get().getEndpoint().equals(request.endpoint())))
            throw new BusinessException(ErrorCode.PUSH_SUBSCRIPTION_CONFLICT);
        var now = clock.instant();
        boolean alreadyActive = existing.filter(s -> s.isEnabled()
                && (s.getExpirationTime() == null || s.getExpirationTime().isAfter(now))).isPresent();
        if (!alreadyActive && subscriptions.countActive(memberId, now) >= 10)
            throw new BusinessException(ErrorCode.PUSH_SUBSCRIPTION_LIMIT);
        var value = existing.orElseGet(() -> PushSubscription.create(member, hash, request, now));
        value.register(request, now);
        return PushSubscriptionResponse.from(subscriptions.saveAndFlush(value));
    }
    public PushSubscriptionResponse get(Long memberId, Long id) { return PushSubscriptionResponse.from(owned(memberId, id)); }
    @Transactional
    public void disable(Long memberId, Long id) { owned(memberId, id).disable(clock.instant()); }
    private PushSubscription owned(Long memberId, Long id) {
        return subscriptions.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUSH_SUBSCRIPTION_NOT_FOUND));
    }
    static String hash(String endpoint) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}

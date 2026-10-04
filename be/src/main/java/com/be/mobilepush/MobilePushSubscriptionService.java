package com.be.mobilepush;

import com.be.global.exception.*;
import com.be.member.entity.Member;
import com.be.member.enums.*;
import com.be.member.repository.MemberRepository;
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
public class MobilePushSubscriptionService {
    private final MobilePushSubscriptionRepository subscriptions;
    private final MemberRepository members;
    private final Clock clock;

    @Transactional
    public MobilePushDtos.Response bind(Long memberId, String secret, MobilePushDtos.Binding request) {
        validateSecret(secret);
        var member = members.findByIdForUpdate(memberId).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        eligible(member);
        var found = subscriptions.findByInstallationId(request.installationId());
        if (found.isEmpty()) {
            var created = MobilePushSubscription.create(member, request.installationId(), hash(secret), request.platform(), clock.instant());
            created.bind(member, request.bindingGeneration(), clock.instant());
            return MobilePushDtos.Response.from(subscriptions.saveAndFlush(created));
        }
        var s = found.get();
        proof(s, secret);
        long generation = request.bindingGeneration();
        if (generation < s.getBindingGeneration() || (generation == s.getBindingGeneration() && !s.getMember().getId().equals(memberId)))
            throw new BusinessException(ErrorCode.MOBILE_PUSH_CONFLICT);
        if (generation > s.getBindingGeneration()) s.bind(member, generation, clock.instant());
        return MobilePushDtos.Response.from(subscriptions.saveAndFlush(s));
    }
    @Transactional
    public MobilePushDtos.Response register(Long memberId, Long id, String secret, MobilePushDtos.Registration request) {
        var s = owned(id, memberId, true); eligible(s.getMember()); proof(s, secret);
        checkVersion(s.getVersion(), request.version());
        String token = request.expoPushToken();
        if (token == null || token.length() > 512 || !token.matches("(?:ExpoPushToken|ExponentPushToken)\\[[A-Za-z0-9_-]+\\]"))
            throw new BusinessException(ErrorCode.INVALID_MOBILE_PUSH);
        String hash = hash(token);
        if (subscriptions.findByTokenHash(hash).filter(other -> !other.getId().equals(id)).isPresent())
            throw new BusinessException(ErrorCode.MOBILE_PUSH_CONFLICT);
        s.register(token, hash, request.platform(), clock.instant());
        return MobilePushDtos.Response.from(subscriptions.saveAndFlush(s));
    }
    public MobilePushDtos.Response get(Long memberId, Long id) { return MobilePushDtos.Response.from(owned(id, memberId, false)); }
    @Transactional
    public void disable(Long memberId, Long id, String secret, long version) {
        var s = owned(id, memberId, true); proof(s, secret); checkVersion(s.getVersion(), version);
        s.disable(clock.instant()); subscriptions.flush();
    }
    private MobilePushSubscription owned(Long id, Long member, boolean lock) {
        return (lock ? subscriptions.ownedForUpdate(id, member) : subscriptions.findByIdAndMemberId(id, member))
                .orElseThrow(() -> new BusinessException(ErrorCode.MOBILE_PUSH_NOT_FOUND));
    }
    static boolean canReceive(Member member) { return member.getStatus() != MemberStatus.RESIGNED && member.getRoles().contains(Role.EMPLOYEE); }
    private static void eligible(Member member) {
        if (!canReceive(member)) throw new BusinessException(ErrorCode.MOBILE_PUSH_FORBIDDEN);
    }
    private static void checkVersion(long actual, long expected) {
        if (actual != expected) throw new BusinessException(ErrorCode.MOBILE_PUSH_CONFLICT);
    }
    private static void validateSecret(String secret) {
        // Client generates at least 32 random bytes encoded as unpadded base64url.
        if (secret == null || !secret.matches("[A-Za-z0-9_-]{43,128}")) throw new BusinessException(ErrorCode.INVALID_MOBILE_PUSH);
    }
    private static void proof(MobilePushSubscription s, String secret) {
        validateSecret(secret);
        if (!MessageDigest.isEqual(s.getInstallationSecretHash().getBytes(StandardCharsets.US_ASCII), hash(secret).getBytes(StandardCharsets.US_ASCII)))
            throw new BusinessException(ErrorCode.MOBILE_PUSH_FORBIDDEN);
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}

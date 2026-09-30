package com.be.push;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpointHash(String hash);
    Optional<PushSubscription> findByIdAndMemberId(Long id, Long memberId);
    @Query("select count(s) from PushSubscription s where s.member.id = :member and s.enabled = true and (s.expirationTime is null or s.expirationTime > :now)")
    long countActive(@Param("member") Long memberId, @Param("now") Instant now);
    @Query("select s.id from PushSubscription s where s.member.id = :member and s.enabled = true and (s.expirationTime is null or s.expirationTime > :now)")
    List<Long> activeIds(@Param("member") Long member, @Param("now") Instant now);
    // A stale 410 must not disable a subscription refreshed after that attempt.
    @Modifying
    @Query("update PushSubscription s set s.enabled = false, s.updatedAt = :now, s.version = s.version + 1 where s.id = :id and s.version = :version")
    int disableVersion(@Param("id") Long id, @Param("version") long version, @Param("now") Instant now);
}

package com.be.mobilepush;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MobilePushSubscriptionRepository extends JpaRepository<MobilePushSubscription, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MobilePushSubscription> findByInstallationId(String installationId);
    Optional<MobilePushSubscription> findByIdAndMemberId(Long id, Long memberId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MobilePushSubscription s where s.id = :id and s.member.id = :member")
    Optional<MobilePushSubscription> ownedForUpdate(@Param("id") Long id, @Param("member") Long member);
    Optional<MobilePushSubscription> findByTokenHash(String tokenHash);
    @Query("select s from MobilePushSubscription s where s.member.id = :member and s.enabled = true and s.expoPushToken is not null")
    List<MobilePushSubscription> active(@Param("member") Long member);
    @Modifying
    @Query("update MobilePushSubscription s set s.enabled = false, s.updatedAt = :now, s.version = s.version + 1 where s.id = :id and s.version = :version and s.enabled = true")
    int disableVersion(@Param("id") Long id, @Param("version") long version, @Param("now") Instant now);
}

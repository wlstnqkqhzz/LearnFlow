package com.be.notification.repository;

import com.be.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByMemberId(Long memberId, Pageable pageable);
    long countByMemberIdAndReadAtIsNull(Long memberId);
    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

    // 조건부 갱신으로 동시에 읽음 처리해도 최초 시각을 보존한다.
    @Modifying
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.member.id = :memberId and n.readAt is null")
    int markRead(@Param("id") Long id, @Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.member.id = :memberId and n.readAt is null")
    int markAllRead(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}

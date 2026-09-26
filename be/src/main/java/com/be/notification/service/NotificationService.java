package com.be.notification.service;

import com.be.enrollment.entity.Enrollment;
import com.be.global.exception.*;
import com.be.notification.dto.*;
import com.be.notification.entity.Notification;
import com.be.notification.enums.NotificationType;
import com.be.notification.repository.NotificationRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notifications;
    private final Clock clock;

    // 실제 생성/전이에 성공한 호출부만 사용. 저장 실패는 원래 업무까지 함께 롤백한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(Enrollment enrollment, NotificationType type) {
        notifications.save(Notification.create(enrollment, type, now()));
    }

    public Page<NotificationResponse> mine(Long memberId, NotificationSearchRequest request) {
        return notifications.findByMemberId(memberId, PageRequest.of(request.page(), request.size(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(NotificationResponse::from);
    }

    public UnreadCountResponse unread(Long memberId) {
        return new UnreadCountResponse(notifications.countByMemberIdAndReadAtIsNull(memberId));
    }

    @Transactional
    public NotificationResponse read(Long memberId, Long id) {
        notifications.markRead(id, memberId, now());
        // 타인 알림과 없는 ID를 동일한 404로 처리해 존재 여부도 노출하지 않는다.
        return NotificationResponse.from(notifications.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND)));
    }

    @Transactional
    public ReadAllResponse readAll(Long memberId) {
        var readAt = now();
        notifications.markAllRead(memberId, readAt);
        return new ReadAllResponse(readAt);
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}

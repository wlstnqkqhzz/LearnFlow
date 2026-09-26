package com.be.notification.dto;

import com.be.notification.entity.Notification;
import com.be.notification.enums.NotificationType;
import java.time.LocalDateTime;

// 회원·수강 Entity나 내부 정보는 직렬화하지 않는다.
public record NotificationResponse(Long notificationId, NotificationType type, String title, String message,
                                   Long relatedEnrollmentId, LocalDateTime readAt, LocalDateTime createdAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getEnrollment().getId(), notification.getReadAt(), notification.getCreatedAt());
    }
}

package com.be.notification.entity;

import com.be.enrollment.entity.Enrollment;
import com.be.member.entity.Member;
import com.be.notification.enums.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 개인 알림: 관련 Entity 전체나 웹 경로 대신 수강 식별자로 이동한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notifications", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = @UniqueConstraint(name = "uk_notifications_enrollment_type", columnNames = {"enrollment_id", "type"}),
        indexes = {
            @Index(name = "idx_notifications_member_created", columnList = "member_id, created_at, id"),
            @Index(name = "idx_notifications_member_read", columnList = "member_id, read_at")
        }, check = @CheckConstraint(name = "chk_notifications_type", constraint =
        "CAST(type AS BINARY) IN ('ENROLLMENT_ASSIGNED', 'COURSE_COMPLETED', 'COURSE_FAILED', 'ENROLLMENT_EXPIRED')"))
public class Notification {
    // 알림 ID
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 수신자 (양방향 연관관계 없음)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_member", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Member member;

    // 알림의 원인이 된 수강
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_enrollment", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Enrollment enrollment;

    // MySQL ENUM 대신 VARCHAR 사용
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    // 발생 당시 사용자에게 표시할 제목과 내용
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String message;

    // UTC 생성 시각 / 최초 읽음 시각 (null이면 읽지 않음)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;
    @Column(name = "read_at", columnDefinition = "datetime(6)")
    private LocalDateTime readAt;

    public static Notification create(Enrollment enrollment, NotificationType type, LocalDateTime now) {
        Notification notification = new Notification();
        notification.enrollment = enrollment;
        notification.member = enrollment.getMember();
        notification.type = type;
        notification.createdAt = now;
        notification.title = switch (type) {
            case ENROLLMENT_ASSIGNED -> "새로운 교육이 배정되었습니다.";
            case COURSE_COMPLETED -> "교육과정을 수료했습니다.";
            case COURSE_FAILED -> "교육과정 이수가 완료되지 않았습니다.";
            case ENROLLMENT_EXPIRED -> "교육 수강 기간이 만료되었습니다.";
        };
        notification.message = enrollment.getCourse().getTitle() + switch (type) {
            case ENROLLMENT_ASSIGNED -> " 교육이 배정되었습니다.";
            case COURSE_COMPLETED -> " 교육과정을 수료했습니다.";
            case COURSE_FAILED -> " 교육의 시험 응시 기회를 모두 사용했습니다.";
            case ENROLLMENT_EXPIRED -> " 교육의 수강 기간이 만료되었습니다.";
        };
        return notification;
    }
}

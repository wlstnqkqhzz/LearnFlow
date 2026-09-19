package com.be.enrollment.entity;

import com.be.course.entity.CourseContent;
import com.be.global.entity.BaseTimeEntity;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

// 콘텐츠 학습 진도 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "content_progresses",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_content_progresses_enrollment_content", columnNames = {"enrollment_id", "course_content_id"})
        },
        indexes = {
                @Index(name = "idx_content_progresses_content", columnList = "course_content_id")
        },
        check = {
                @CheckConstraint(name = "chk_content_progresses_progress_rate",
                        constraint = "progress_rate BETWEEN 0 AND 100"),
                @CheckConstraint(name = "chk_content_progresses_completed_at",
                        constraint = "(progress_rate = 100 AND completed_at IS NOT NULL) OR (progress_rate < 100 AND completed_at IS NULL)"),
                @CheckConstraint(name = "chk_content_progresses_version",
                        constraint = "version >= 0")
        })
public class ContentProgress extends BaseTimeEntity {

    // 콘텐츠 학습 진도 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 해당 학습 또는 응시가 속한 수강 배정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_content_progresses_enrollment", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Enrollment enrollment;

    // 진도를 기록할 교육 콘텐츠
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_content_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_content_progresses_content", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private CourseContent courseContent;

    // 콘텐츠 진도율 (0~100)
    @ColumnDefault("0.00")
    @Column(name = "progress_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressRate = new BigDecimal("0.00");

    // 콘텐츠 진도율 100% 달성 시각 (UTC)
    @Column(name = "completed_at", nullable = true, columnDefinition = "datetime(6)")
    private LocalDateTime completedAt;

    // 동시 수정 충돌을 감지하는 낙관적 잠금 버전
    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // 최초 진도 생성: 소속과 접근 권한은 Service에서 검증
    public static ContentProgress create(Enrollment enrollment, CourseContent content) {
        ContentProgress progress = new ContentProgress();
        progress.enrollment = java.util.Objects.requireNonNull(enrollment);
        progress.courseContent = java.util.Objects.requireNonNull(content);
        return progress;
    }

    // DB 정밀도에 맞는 값만 허용하고 완료 시각은 서버에서 관리
    public void updateProgress(BigDecimal rate, LocalDateTime now) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0
                || rate.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(ErrorCode.INVALID_PROGRESS_RATE);
        }
        this.progressRate = rate.setScale(2);
        if (rate.compareTo(new BigDecimal("100")) == 0) {
            if (completedAt == null) completedAt = java.util.Objects.requireNonNull(now);
        } else {
            completedAt = null;
        }
    }
}

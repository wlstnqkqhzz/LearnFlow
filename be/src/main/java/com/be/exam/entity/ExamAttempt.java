package com.be.exam.entity;

import com.be.enrollment.entity.Enrollment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 시험 응시 이력 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "exam_attempts",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_exam_attempts_enrollment_exam_number", columnNames = {"enrollment_id", "exam_id", "attempt_number"})
        },
        indexes = {
                @Index(name = "idx_exam_attempts_exam_submitted", columnList = "exam_id, submitted_at")
        },
        check = {
                @CheckConstraint(name = "chk_exam_attempts_attempt_number",
                        constraint = "attempt_number > 0"),
                @CheckConstraint(name = "chk_exam_attempts_submission",
                        constraint = "(submitted_at IS NULL AND score IS NULL AND passed IS NULL) OR (submitted_at IS NOT NULL AND submitted_at >= started_at AND score IS NOT NULL AND score BETWEEN 0 AND 100 AND passed IS NOT NULL AND passed IN (0, 1))")
        })
public class ExamAttempt {

    // 시험 응시 이력 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 소속 시험
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exam_attempts_exam", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Exam exam;

    // 해당 학습 또는 응시가 속한 수강 배정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exam_attempts_enrollment", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Enrollment enrollment;

    // 응시 번호 (1부터 시작)
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    // 획득 배점 합계 / 전체 배점 합계 × 100 (제출 전 null)
    @Column(name = "score", nullable = true, precision = 5, scale = 2)
    private BigDecimal score;

    // 시험 합격 여부 (제출·채점 전에는 null)
    @Column(name = "passed", nullable = true, columnDefinition = "boolean")
    private Boolean passed;

    // 시험 응시 시작 시각 (UTC)
    @Column(name = "started_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime startedAt;

    // 시험 제출 시각 (UTC, 미제출이면 null)
    @Column(name = "submitted_at", nullable = true, columnDefinition = "datetime(6)")
    private LocalDateTime submittedAt;

    // 시작 시점에는 점수·합격 여부·제출 시각을 모두 null로 유지
    public static ExamAttempt start(Enrollment enrollment, Exam exam, int number, LocalDateTime now) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.enrollment = enrollment;
        attempt.exam = exam;
        attempt.attemptNumber = number;
        attempt.startedAt = now;
        return attempt;
    }

    public void requireUnsubmitted() {
        if (submittedAt != null) throw new com.be.global.exception.BusinessException(
                com.be.global.exception.ErrorCode.EXAM_ATTEMPT_ALREADY_SUBMITTED);
    }

    // 채점 및 수강 상태 변경과 동일 트랜잭션에서 한 번만 확정
    public void submit(BigDecimal score, boolean passed, LocalDateTime now) {
        requireUnsubmitted();
        this.score = score;
        this.passed = passed;
        this.submittedAt = now;
    }
}

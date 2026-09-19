package com.be.enrollment.entity;

import com.be.assignment.entity.AssignmentRule;
import com.be.course.entity.Course;
import com.be.enrollment.enums.AssignmentSource;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.global.entity.BaseTimeEntity;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.member.entity.Member;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 교육 수강 배정 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "enrollments",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_enrollments_member_course", columnNames = {"member_id", "course_id"})
        },
        indexes = {
                @Index(name = "idx_enrollments_member_status_due", columnList = "member_id, status, due_date"),
                @Index(name = "idx_enrollments_course_status", columnList = "course_id, status"),
                @Index(name = "idx_enrollments_status_due", columnList = "status, due_date"),
                @Index(name = "idx_enrollments_assignment_rule", columnList = "assignment_rule_id")
        },
        check = {
                @CheckConstraint(name = "chk_enrollments_assignment_source_rule",
                        constraint = "(assignment_source = 'MANUAL' AND assignment_rule_id IS NULL) OR (assignment_source = 'AUTOMATIC' AND assignment_rule_id IS NOT NULL)"),
                @CheckConstraint(name = "chk_enrollments_status_timestamps",
                        constraint = "(status = 'ASSIGNED' AND started_at IS NULL AND completed_at IS NULL) OR (status = 'IN_PROGRESS' AND started_at IS NOT NULL AND completed_at IS NULL) OR (status = 'COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL) OR (status = 'FAILED' AND started_at IS NOT NULL AND completed_at IS NULL) OR (status = 'EXPIRED' AND completed_at IS NULL)"),
                @CheckConstraint(name = "chk_enrollments_started_at",
                        constraint = "started_at IS NULL OR started_at >= assigned_at"),
                @CheckConstraint(name = "chk_enrollments_completed_at",
                        constraint = "completed_at IS NULL OR completed_at >= started_at"),
                @CheckConstraint(name = "chk_enrollments_version",
                        constraint = "version >= 0"),
                @CheckConstraint(name = "chk_enrollments_status",
                        constraint = "CAST(status AS BINARY) IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'EXPIRED')"),
                @CheckConstraint(name = "chk_enrollments_assignment_source",
                        constraint = "CAST(assignment_source AS BINARY) IN ('MANUAL', 'AUTOMATIC')")
        })
public class Enrollment extends BaseTimeEntity {

    // 교육 수강 배정 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 교육을 배정받은 회원
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_enrollments_member", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Member member;

    // 소속 교육과정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_enrollments_course", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Course course;

    // 수강 상태 (배정 / 진행 / 수료 / 실패 / 만료)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'ASSIGNED'")
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ASSIGNED;

    // 배정 출처 (수동 / 자동)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "assignment_source", nullable = false, length = 20)
    private AssignmentSource assignmentSource;

    // 최초 자동 배정에 사용한 규칙 (수동 배정이면 null)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assignment_rule_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_enrollments_assignment_rule", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private AssignmentRule assignmentRule;

    // 교육 배정 시각 (UTC)
    @Column(name = "assigned_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime assignedAt;

    // 최초 콘텐츠 학습 또는 시험 응시 시작 시각 (UTC)
    @Column(name = "started_at", nullable = true, columnDefinition = "datetime(6)")
    private LocalDateTime startedAt;

    // 교육 수료 시각 (UTC, COMPLETED 상태에서만 존재)
    @Column(name = "completed_at", nullable = true, columnDefinition = "datetime(6)")
    private LocalDateTime completedAt;

    // 수강 마감일 - 배정 당시 과정 종료일을 보존
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    // 동시 수정 충돌을 감지하는 낙관적 잠금 버전
    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // 최종 상태에서는 콘텐츠 진도를 새로 만들거나 변경할 수 없음
    public void requireProgressEditable() {
        if (status != EnrollmentStatus.ASSIGNED && status != EnrollmentStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ENROLLMENT_PROGRESS_NOT_EDITABLE);
        }
    }

    // 양수 진도가 처음 기록된 경우에만 호출하며 최초 시작 시각을 보존
    public void startLearning(LocalDateTime now) {
        requireProgressEditable();
        if (status == EnrollmentStatus.ASSIGNED) {
            status = EnrollmentStatus.IN_PROGRESS;
            if (startedAt == null) startedAt = java.util.Objects.requireNonNull(now);
        }
    }

    // 수료 조건 판정 후 진행 중인 수강만 완료 (ASSIGNED 직접 완료 금지)
    public void completeLearning(LocalDateTime now) {
        if (status == EnrollmentStatus.IN_PROGRESS) {
            status = EnrollmentStatus.COMPLETED;
            if (completedAt == null) completedAt = java.util.Objects.requireNonNull(now);
        }
    }

    // 수동 배정: 자동 규칙 참조 없이 ASSIGNED로 생성
    public static Enrollment manual(Member member, Course course, LocalDateTime assignedAt) {
        return assigned(member, course, AssignmentSource.MANUAL, null, assignedAt);
    }

    // 자동 배정: 최초 배정 규칙을 보존
    public static Enrollment automatic(Member member, AssignmentRule rule, LocalDateTime assignedAt) {
        java.util.Objects.requireNonNull(rule);
        return assigned(member, rule.getCourse(), AssignmentSource.AUTOMATIC, rule, assignedAt);
    }

    private static Enrollment assigned(Member member, Course course, AssignmentSource source,
                                       AssignmentRule rule, LocalDateTime assignedAt) {
        Enrollment enrollment = new Enrollment();
        enrollment.member = java.util.Objects.requireNonNull(member);
        enrollment.course = java.util.Objects.requireNonNull(course);
        enrollment.assignmentSource = source;
        enrollment.assignmentRule = rule;
        enrollment.assignedAt = java.util.Objects.requireNonNull(assignedAt);
        enrollment.dueDate = java.util.Objects.requireNonNull(course.getEndDate());
        return enrollment;
    }
}

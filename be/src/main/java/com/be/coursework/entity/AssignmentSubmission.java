package com.be.coursework.entity;

import com.be.coursework.enums.*;
import com.be.enrollment.entity.Enrollment;
import com.be.global.entity.BaseTimeEntity;
import com.be.global.exception.*;
import com.be.member.entity.Member;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "assignment_submissions", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    uniqueConstraints = @UniqueConstraint(name = "uk_assignment_submissions_assignment_enrollment", columnNames = {"assignment_id", "enrollment_id"}),
    indexes = {
        @Index(name = "idx_submissions_enrollment_passed", columnList = "enrollment_id, passed, assignment_id"),
        @Index(name = "idx_submissions_assignment_graded", columnList = "assignment_id, graded_at, submitted_at")
    }, check = {
        @CheckConstraint(name = "chk_submissions_type", constraint = "CAST(submission_type AS BINARY) IN ('TEXT', 'URL')"),
        @CheckConstraint(name = "chk_submissions_count", constraint = "submission_count > 0"),
        @CheckConstraint(name = "chk_submissions_version", constraint = "version >= 0"),
        @CheckConstraint(name = "chk_submissions_grading", constraint = "(score IS NULL AND passed IS NULL AND feedback IS NULL AND graded_at IS NULL AND graded_by_member_id IS NULL) OR (score IS NOT NULL AND score BETWEEN 0 AND 100 AND passed IS NOT NULL AND passed IN (0, 1) AND graded_at IS NOT NULL AND graded_at >= submitted_at AND graded_by_member_id IS NOT NULL)")
    })
public class AssignmentSubmission extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_submissions_assignment", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Assignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_submissions_enrollment", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Enrollment enrollment;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "submission_type", nullable = false, length = 20)
    private AssignmentSubmissionType submissionType;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "submitted_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime submittedAt;
    @Column(name = "submission_count", nullable = false)
    private int submissionCount;
    @Column(precision = 5, scale = 2)
    private BigDecimal score;
    @Column(columnDefinition = "boolean")
    private Boolean passed;
    @Column(columnDefinition = "text")
    private String feedback;
    @Column(name = "graded_at", columnDefinition = "datetime(6)")
    private LocalDateTime gradedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by_member_id", foreignKey = @ForeignKey(name = "fk_submissions_grader", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Member gradedBy;
    @Version @Column(nullable = false)
    private Long version;

    public static AssignmentSubmission create(Assignment assignment, Enrollment enrollment,
            AssignmentSubmissionType type, String content, LocalDateTime now) {
        var submission = new AssignmentSubmission();
        submission.assignment = assignment;
        submission.enrollment = enrollment;
        submission.resubmit(type, content, now);
        return submission;
    }
    public void resubmit(AssignmentSubmissionType type, String content, LocalDateTime now) {
        if (Boolean.TRUE.equals(passed)) throw new BusinessException(ErrorCode.ASSIGNMENT_ALREADY_PASSED);
        this.submissionType = type;
        this.content = content;
        this.submittedAt = now;
        this.submissionCount = Math.incrementExact(submissionCount);
        this.score = null;
        this.passed = null;
        this.feedback = null;
        this.gradedAt = null;
        this.gradedBy = null;
    }
    public void grade(BigDecimal score, String feedback, Member grader, LocalDateTime now) {
        if (gradedAt != null) throw new BusinessException(ErrorCode.ASSIGNMENT_ALREADY_GRADED);
        this.score = score;
        this.passed = score.compareTo(assignment.getPassingScore()) >= 0;
        this.feedback = feedback;
        this.gradedAt = now;
        this.gradedBy = grader;
    }
    public AssignmentSubmissionStatus status() {
        return gradedAt == null ? AssignmentSubmissionStatus.PENDING_GRADING
                : Boolean.TRUE.equals(passed) ? AssignmentSubmissionStatus.PASSED : AssignmentSubmissionStatus.FAILED;
    }
}

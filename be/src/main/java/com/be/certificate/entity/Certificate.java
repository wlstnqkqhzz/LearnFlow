package com.be.certificate.entity;

import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

// 최초 발급 당시의 증명 정보를 보존한다. 수정/삭제 API 및 cascade는 두지 않는다.
@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "certificates", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_certificates_enrollment", columnNames = "enrollment_id"),
                @UniqueConstraint(name = "uk_certificates_number", columnNames = "certificate_number")
        }, check = @CheckConstraint(name = "chk_certificates_dates", constraint = "issued_at >= completed_at"))
public class Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_certificates_enrollment", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Enrollment enrollment;

    @Column(name = "certificate_number", nullable = false, updatable = false, length = 64)
    private String certificateNumber;

    @Column(name = "issued_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private LocalDateTime issuedAt;

    @Column(name = "member_name", nullable = false, updatable = false, length = 100)
    private String memberName;

    @Column(name = "course_title", nullable = false, updatable = false, length = 200)
    private String courseTitle;

    @Column(name = "completed_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private LocalDateTime completedAt;

    public static Certificate issue(Enrollment enrollment, LocalDateTime issuedAt) {
        Objects.requireNonNull(enrollment);
        Objects.requireNonNull(issuedAt);
        if (enrollment.getStatus() != EnrollmentStatus.COMPLETED || enrollment.getCompletedAt() == null) {
            throw new BusinessException(ErrorCode.CERTIFICATE_NOT_ELIGIBLE);
        }
        if (issuedAt.isBefore(enrollment.getCompletedAt())) {
            throw new IllegalStateException("Certificate issuance precedes completion");
        }
        var certificate = new Certificate();
        certificate.enrollment = enrollment;
        certificate.certificateNumber = "LF-CERT-" + UUID.randomUUID().toString().replace("-", "");
        certificate.issuedAt = issuedAt;
        certificate.memberName = Objects.requireNonNull(enrollment.getMember().getName());
        certificate.courseTitle = Objects.requireNonNull(enrollment.getCourse().getTitle());
        certificate.completedAt = enrollment.getCompletedAt();
        return certificate;
    }
}

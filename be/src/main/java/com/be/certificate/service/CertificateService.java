package com.be.certificate.service;

import com.be.certificate.dto.CertificateResponse;
import com.be.certificate.entity.Certificate;
import com.be.certificate.repository.CertificateRepository;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.global.security.MemberPrincipal;
import com.be.member.enums.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {
    private final EnrollmentRepository enrollments;
    private final CertificateRepository certificates;
    private final Clock clock;

    public record IssueResult(CertificateResponse certificate, boolean created) {}

    // 잠금 획득 이후 선행 요청이 커밋한 수료증을 읽는다. 연관 엔티티에는 잠금을 전파하지 않는다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IssueResult issue(@NotNull @Positive Long id, @NotNull MemberPrincipal principal) {
        requireRole(principal);
        var enrollment = enrollments.findForCertificateIssue(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        requireAccessibleAndCompleted(enrollment, principal);
        var existing = certificates.findByEnrollmentId(id);
        if (existing.isPresent()) return new IssueResult(CertificateResponse.from(existing.get()), false);
        var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var certificate = certificates.saveAndFlush(Certificate.issue(enrollment, now));
        return new IssueResult(CertificateResponse.from(certificate), true);
    }

    public CertificateResponse get(@NotNull @Positive Long id, @NotNull MemberPrincipal principal) {
        requireRole(principal);
        var enrollment = enrollments.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        requireAccessibleAndCompleted(enrollment, principal);
        return certificates.findByEnrollmentId(id).map(CertificateResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATE_NOT_FOUND));
    }

    private void requireRole(MemberPrincipal principal) {
        if (!principal.roles().contains(Role.ADMIN) && !principal.roles().contains(Role.EMPLOYEE)) {
            throw new BusinessException(ErrorCode.CERTIFICATE_ACCESS_DENIED);
        }
    }

    private void requireAccessibleAndCompleted(Enrollment enrollment, MemberPrincipal principal) {
        if (!principal.roles().contains(Role.ADMIN)
                && !enrollment.getMember().getId().equals(principal.memberId())) {
            throw new BusinessException(ErrorCode.CERTIFICATE_ACCESS_DENIED);
        }
        if (enrollment.getStatus() != EnrollmentStatus.COMPLETED || enrollment.getCompletedAt() == null) {
            throw new BusinessException(ErrorCode.CERTIFICATE_NOT_ELIGIBLE);
        }
    }
}

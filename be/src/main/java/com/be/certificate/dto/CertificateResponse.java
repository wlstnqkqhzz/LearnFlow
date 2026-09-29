package com.be.certificate.dto;

import com.be.certificate.entity.Certificate;
import java.time.LocalDateTime;

// 시각은 기존 API와 동일한 UTC LocalDateTime. PDF에는 Asia/Seoul 날짜로 표시한다.
public record CertificateResponse(Long enrollmentId, String certificateNumber, String memberName,
                                  String courseTitle, LocalDateTime completedAt, LocalDateTime issuedAt) {
    public static CertificateResponse from(Certificate certificate) {
        return new CertificateResponse(certificate.getEnrollment().getId(), certificate.getCertificateNumber(),
                certificate.getMemberName(), certificate.getCourseTitle(),
                certificate.getCompletedAt(), certificate.getIssuedAt());
    }
}

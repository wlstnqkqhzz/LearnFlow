package com.be.certificate.controller;

import com.be.certificate.dto.CertificateResponse;
import com.be.certificate.service.CertificatePdfRenderer;
import com.be.certificate.service.CertificateService;
import com.be.global.security.MemberPrincipal;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/enrollments/{id}/certificate")
public class CertificateController {
    private final CertificateService certificates;
    private final CertificatePdfRenderer pdf;

    @PutMapping
    public ResponseEntity<CertificateResponse> issue(@PathVariable @Positive Long id,
                                                    @AuthenticationPrincipal MemberPrincipal principal) {
        var result = certificates.issue(id, principal);
        var response = result.created()
                ? ResponseEntity.created(URI.create("/api/enrollments/" + id + "/certificate"))
                : ResponseEntity.ok();
        return response.cacheControl(CacheControl.noStore()).body(result.certificate());
    }

    @GetMapping
    public ResponseEntity<CertificateResponse> get(@PathVariable @Positive Long id,
                                                  @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(certificates.get(id, principal));
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> download(@PathVariable @Positive Long id,
                                          @AuthenticationPrincipal MemberPrincipal principal) {
        var certificate = certificates.get(id, principal);
        // 읽기 트랜잭션 종료 후 전체 PDF를 생성한다. 실패해도 발급/수강 데이터는 변경하지 않는다.
        byte[] bytes = pdf.render(certificate);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("certificate-" + certificate.certificateNumber() + ".pdf").build().toString())
                .cacheControl(CacheControl.noStore()).contentLength(bytes.length).body(bytes);
    }
}

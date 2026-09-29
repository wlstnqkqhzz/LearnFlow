package com.be.certificate;

import com.be.certificate.controller.CertificateController;
import com.be.certificate.entity.Certificate;
import com.be.certificate.repository.CertificateRepository;
import com.be.certificate.service.*;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.global.config.*;
import com.be.global.exception.*;
import com.be.global.security.*;
import com.be.member.enums.Role;
import com.be.security.JwtTestSupport;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static com.be.certificate.CertificateFixtures.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CertificateController.class)
@Import({SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class, CertificateService.class})
class CertificateApiTest {
    private static final String URL = "/api/enrollments/3/certificate";
    @Autowired MockMvc mvc;
    @MockitoBean EnrollmentRepository enrollments;
    @MockitoBean CertificateRepository certificates;
    @MockitoBean CertificatePdfRenderer pdf;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService authenticatedMembers;
    @MockitoBean Clock clock;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("jwt.secret", JwtTestSupport::secret);
        r.add("jwt.access-token-ttl-seconds", () -> 300);
        r.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }
    @BeforeEach void setup() {
        var enrollment = completed();
        when(enrollments.findById(3L)).thenReturn(Optional.of(enrollment));
        when(enrollments.findForCertificateIssue(3L)).thenReturn(Optional.of(enrollment));
        when(clock.instant()).thenReturn(CLOCK.instant());
        when(certificates.saveAndFlush(any())).thenAnswer(c -> c.getArgument(0));
    }
    @Test void issueReturns201ThenExisting200WithSameNumberAndCompleteDto() throws Exception {
        mvc.perform(put(URL).with(as(OWNER))).andExpect(status().isCreated())
                .andExpect(header().string("Location", URL)).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.enrollmentId").value(3))
                .andExpect(jsonPath("$.memberName").value("직원2"))
                .andExpect(jsonPath("$.courseTitle").value("교육"))
                .andExpect(jsonPath("$.completedAt").value("2026-09-20T16:30:00"))
                .andExpect(jsonPath("$.issuedAt").value("2026-09-29T16:30:00.123456"));
        var saved = issued();
        mvc.perform(put(URL).with(as(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.certificateNumber").value(saved.getCertificateNumber()));
        mvc.perform(get(URL).with(as(OWNER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.certificateNumber").value(saved.getCertificateNumber()));
    }
    @Test void pdfHasSafeAttachmentHeadersAndNoImplicitIssuance() throws Exception {
        mvc.perform(get(URL + "/pdf").with(as(OWNER))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CERTIFICATE_NOT_FOUND"));
        verifyNoInteractions(pdf);
        var saved = issued();
        byte[] bytes = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(pdf.render(any())).thenReturn(bytes);
        mvc.perform(get(URL + "/pdf").with(as(ADMIN))).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf")).andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"certificate-" + saved.getCertificateNumber() + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(certificates, never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(strings = {"", "/pdf"})
    void readsRejectForeignEmployeeInstructorAndAnonymous(String suffix) throws Exception {
        issued();
        mvc.perform(get(URL + suffix).with(as(principal(9, Role.EMPLOYEE)))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CERTIFICATE_ACCESS_DENIED"));
        mvc.perform(get(URL + suffix).with(as(principal(2, Role.INSTRUCTOR)))).andExpect(status().isForbidden());
        mvc.perform(get(URL + suffix)).andExpect(status().isUnauthorized());
        verifyNoInteractions(pdf);
    }
    @Test void writesRejectForeignEmployeeInstructorAndAnonymousAndValidateId() throws Exception {
        mvc.perform(put(URL).with(as(principal(9, Role.EMPLOYEE)))).andExpect(status().isForbidden());
        mvc.perform(put(URL).with(as(principal(2, Role.INSTRUCTOR)))).andExpect(status().isForbidden());
        mvc.perform(put(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/enrollments/0/certificate").with(as(OWNER))).andExpect(status().isBadRequest());
        mvc.perform(post(URL).with(as(OWNER))).andExpect(status().isForbidden());
        verify(certificates, never()).saveAndFlush(any());
    }
    @Test void pdfFailureIsSanitizedAndExistingCertificateRemainsReadable() throws Exception {
        var saved = issued();
        when(pdf.render(any())).thenThrow(new BusinessException(ErrorCode.CERTIFICATE_PDF_GENERATION_FAILED));
        mvc.perform(get(URL + "/pdf").with(as(OWNER))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CERTIFICATE_PDF_GENERATION_FAILED"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
        mvc.perform(get(URL).with(as(OWNER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.certificateNumber").value(saved.getCertificateNumber()));
        verify(certificates, never()).saveAndFlush(any());
    }
    @Test void lockConflictUsesExistingSanitized409Contract() throws Exception {
        when(enrollments.findForCertificateIssue(3L)).thenThrow(new CannotAcquireLockException("internal DB details"));
        mvc.perform(put(URL).with(as(OWNER))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }
    private Certificate issued() {
        var value = Certificate.issue(completed(), LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        when(certificates.findByEnrollmentId(3L)).thenReturn(Optional.of(value));
        return value;
    }
    private RequestPostProcessor as(MemberPrincipal principal) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
    }
}

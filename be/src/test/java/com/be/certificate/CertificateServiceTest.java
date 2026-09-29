package com.be.certificate;

import com.be.certificate.entity.Certificate;
import com.be.certificate.repository.CertificateRepository;
import com.be.certificate.service.CertificateService;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.member.enums.Role;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import static com.be.certificate.CertificateFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificateServiceTest {
    EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    CertificateRepository certificates = mock(CertificateRepository.class);
    CertificateService service = new CertificateService(enrollments, certificates, CLOCK);

    @BeforeEach void setup() {
        var enrollment = completed();
        when(enrollments.findForCertificateIssue(3L)).thenReturn(Optional.of(enrollment));
        when(enrollments.findById(3L)).thenReturn(Optional.of(enrollment));
        when(certificates.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void completedIssuesImmutableSnapshotWithAnonymousNumberAndMicrosecondTimestamp() {
        var result = service.issue(3L, OWNER);
        assertThat(result.created()).isTrue();
        assertThat(result.certificate().certificateNumber()).matches("LF-CERT-[0-9a-f]{32}");
        assertThat(result.certificate().memberName()).isEqualTo("직원2");
        assertThat(result.certificate().courseTitle()).isEqualTo("교육");
        assertThat(result.certificate().completedAt()).isEqualTo(COMPLETED);
        assertThat(result.certificate().issuedAt().getNano()).isEqualTo(123456000);
        assertThat(enrollments.findById(3L).orElseThrow().getVersion()).isZero();
    }

    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void incompleteStatusCannotIssueOrRead(EnrollmentStatus status) {
        var enrollment = enrollments.findById(3L).orElseThrow();
        ReflectionTestUtils.setField(enrollment, "status", status);
        assertCode(() -> service.issue(3L, OWNER), ErrorCode.CERTIFICATE_NOT_ELIGIBLE);
        assertCode(() -> service.get(3L, OWNER), ErrorCode.CERTIFICATE_NOT_ELIGIBLE);
        verifyNoInteractions(certificates);
    }

    @Test void missingCompletionTimestampCannotIssue() {
        ReflectionTestUtils.setField(enrollments.findById(3L).orElseThrow(), "completedAt", null);
        assertCode(() -> service.issue(3L, OWNER), ErrorCode.CERTIFICATE_NOT_ELIGIBLE);
        verifyNoInteractions(certificates);
    }

    @Test void ownerOnlyAndAdminOverride() {
        assertCode(() -> service.issue(3L, principal(9, Role.EMPLOYEE)), ErrorCode.CERTIFICATE_ACCESS_DENIED);
        assertCode(() -> service.get(3L, principal(9, Role.EMPLOYEE)), ErrorCode.CERTIFICATE_ACCESS_DENIED);
        assertThat(service.issue(3L, ADMIN).created()).isTrue();
    }

    @Test void instructorOnlyRejectedEvenForOwnEnrollmentBeforeLoadingData() {
        clearInvocations(enrollments);
        assertCode(() -> service.issue(3L, principal(2, Role.INSTRUCTOR)), ErrorCode.CERTIFICATE_ACCESS_DENIED);
        assertCode(() -> service.get(3L, principal(2, Role.INSTRUCTOR)), ErrorCode.CERTIFICATE_ACCESS_DENIED);
        verifyNoInteractions(enrollments, certificates);
    }

    @Test void reissueAndReadReturnOriginalSnapshotAfterNamesChange() {
        var enrollment = enrollments.findById(3L).orElseThrow();
        var certificate = Certificate.issue(enrollment, LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        when(certificates.findByEnrollmentId(3L)).thenReturn(Optional.of(certificate));
        ReflectionTestUtils.setField(enrollment.getMember(), "name", "변경된 이름");
        ReflectionTestUtils.setField(enrollment.getCourse(), "title", "변경된 과정");
        var result = service.issue(3L, OWNER);
        assertThat(result.created()).isFalse();
        assertThat(result.certificate()).isEqualTo(service.get(3L, ADMIN));
        assertThat(result.certificate().certificateNumber()).isEqualTo(certificate.getCertificateNumber());
        assertThat(result.certificate().memberName()).isEqualTo("직원2");
        assertThat(result.certificate().courseTitle()).isEqualTo("교육");
        verify(certificates, never()).saveAndFlush(any());
    }

    @Test void getNeverCreatesAndUnknownEnrollmentIsNotFound() {
        assertCode(() -> service.get(3L, OWNER), ErrorCode.CERTIFICATE_NOT_FOUND);
        assertCode(() -> service.get(99L, ADMIN), ErrorCode.ENROLLMENT_NOT_FOUND);
        assertCode(() -> service.issue(99L, ADMIN), ErrorCode.ENROLLMENT_NOT_FOUND);
        verify(certificates, never()).saveAndFlush(any());
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}

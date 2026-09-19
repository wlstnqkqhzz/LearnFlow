package com.be.enrollment.controller;

import com.be.enrollment.dto.*;
import com.be.enrollment.service.EnrollmentService;
import com.be.global.dto.PageResponse;
import com.be.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 관리용 배정 API와 인증 주체 기반 본인 조회 (학습 상태 변경·삭제 없음)
@RestController
@RequiredArgsConstructor
public class EnrollmentController {
    private final EnrollmentService service;

    @PostMapping("/api/courses/{courseId}/enrollments")
    public ResponseEntity<EnrollmentResponse> assign(@PathVariable @Positive Long courseId,
            @Valid @RequestBody ManualEnrollmentRequest request) {
        var response = service.assignManually(courseId, request);
        return ResponseEntity.created(URI.create("/api/enrollments/" + response.enrollmentId())).body(response);
    }

    @GetMapping("/api/courses/{courseId}/enrollments")
    public PageResponse<EnrollmentResponse> forCourse(@PathVariable @Positive Long courseId,
            @Valid @ModelAttribute EnrollmentSearchRequest request) {
        return PageResponse.from(service.forCourse(courseId, request));
    }

    @GetMapping("/api/enrollments/me")
    public PageResponse<EnrollmentResponse> mine(@AuthenticationPrincipal MemberPrincipal principal,
            @Valid @ModelAttribute EnrollmentSearchRequest request) {
        return PageResponse.from(service.forMember(principal.memberId(), request));
    }

    @GetMapping("/api/enrollments/{enrollmentId}")
    public EnrollmentResponse get(@PathVariable @Positive Long enrollmentId) {
        return service.get(enrollmentId);
    }
}

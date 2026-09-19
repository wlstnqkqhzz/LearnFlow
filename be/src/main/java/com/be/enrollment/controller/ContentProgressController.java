package com.be.enrollment.controller;

import com.be.enrollment.dto.*;
import com.be.enrollment.service.ContentProgressService;
import com.be.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 인증 주체는 SecurityContext에서만 전달하며 회원 ID를 요청으로 받지 않음
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/enrollments/{enrollmentId}")
public class ContentProgressController {
    private final ContentProgressService service;

    @GetMapping("/progress")
    public EnrollmentProgressResponse get(@PathVariable @Positive Long enrollmentId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.get(enrollmentId, principal);
    }

    @PatchMapping("/contents/{contentId}/progress")
    public EnrollmentProgressResponse update(@PathVariable @Positive Long enrollmentId,
            @PathVariable @Positive Long contentId, @AuthenticationPrincipal MemberPrincipal principal,
            @RequestBody @Valid ContentProgressUpdateRequest request) {
        return service.update(enrollmentId, contentId, principal, request);
    }
}

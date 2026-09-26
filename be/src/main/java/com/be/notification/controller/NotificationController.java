package com.be.notification.controller;

import com.be.global.dto.PageResponse;
import com.be.global.security.MemberPrincipal;
import com.be.notification.dto.*;
import com.be.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 모든 역할이 인증된 본인의 알림만 조회/읽음 처리한다.
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping("/me")
    public PageResponse<NotificationResponse> mine(@AuthenticationPrincipal MemberPrincipal principal,
            @Valid @ModelAttribute NotificationSearchRequest request) {
        return PageResponse.from(service.mine(principal.memberId(), request));
    }
    @GetMapping("/me/unread-count")
    public UnreadCountResponse unread(@AuthenticationPrincipal MemberPrincipal principal) {
        return service.unread(principal.memberId());
    }
    @PatchMapping("/{notificationId}/read")
    public NotificationResponse read(@AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable @Positive Long notificationId) {
        return service.read(principal.memberId(), notificationId);
    }
    @PatchMapping("/me/read-all")
    public ReadAllResponse readAll(@AuthenticationPrincipal MemberPrincipal principal) {
        return service.readAll(principal.memberId());
    }
}

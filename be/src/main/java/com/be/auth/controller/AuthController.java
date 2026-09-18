package com.be.auth.controller;

import com.be.auth.dto.*;
import com.be.auth.service.AuthService;
import com.be.global.security.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

// 로그인·재발급·로그아웃 API
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    // Refresh Token 검증 후 새 Access/Refresh Token 반환
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.refresh(request));
    }

    // 요청 본문의 회원 ID 대신 인증된 회원의 Refresh Token 삭제
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal MemberPrincipal principal) {
        authService.logout(principal.memberId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.login(request));
    }
}

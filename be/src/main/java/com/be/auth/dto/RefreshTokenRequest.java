package com.be.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 재발급 전용 요청: Refresh Token은 Authorization 헤더가 아닌 본문으로 전달
public record RefreshTokenRequest(@NotBlank String refreshToken) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=REDACTED]";
    }
}

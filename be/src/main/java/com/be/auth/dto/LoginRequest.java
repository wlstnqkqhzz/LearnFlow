package com.be.auth.dto;

import com.be.member.entity.Member;
import jakarta.validation.constraints.*;

// 로그인 요청 - 이메일만 정규화하며 비밀번호 원문은 그대로 검증
public record LoginRequest(@NotBlank @Email @Size(max = 255) String email,
                           @NotBlank @Size(max = 128) String password) {
    public LoginRequest {
        email = Member.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[password=REDACTED]";
    }
}

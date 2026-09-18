package com.be.member.dto;

import com.be.member.entity.Member;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

// 일반 정보 전체 수정 - 사번, 비밀번호, 소속, 상태, 역할은 포함하지 않음
public record MemberUpdateRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate hireDate
) {
    public MemberUpdateRequest {
        email = Member.normalizeEmail(email);
        name = name == null ? null : name.trim();
    }
}

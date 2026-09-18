package com.be.member.dto;

import com.be.member.entity.Member;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

// 직원 생성 입력 - 원문 비밀번호는 요청과 해시 처리 단계에서만 사용
public record MemberCreateRequest(
        @NotBlank @Size(max = 50) String employeeNumber,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive Long departmentId,
        @NotNull @Positive Long jobPositionId,
        @NotNull LocalDate hireDate
) {
    public MemberCreateRequest {
        employeeNumber = employeeNumber == null ? null : employeeNumber.trim();
        email = Member.normalizeEmail(email);
        name = name == null ? null : name.trim();
    }

    // 요청 객체를 로그로 출력해도 원문 비밀번호가 노출되지 않도록 보호
    @Override
    public String toString() {
        return "MemberCreateRequest[password=REDACTED]";
    }
}

package com.be.organization.dto;

import jakarta.validation.constraints.*;

// 직무 생성 입력 - 코드는 생성 시에만 지정
public record JobPositionCreateRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name
) {
    public JobPositionCreateRequest {
        code = code == null ? null : code.trim();
        name = name == null ? null : name.trim();
    }
}

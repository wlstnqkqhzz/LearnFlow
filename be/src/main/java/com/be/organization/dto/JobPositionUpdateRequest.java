package com.be.organization.dto;

import jakarta.validation.constraints.*;

// 직무 수정 입력
public record JobPositionUpdateRequest(
        @NotBlank @Size(max = 100) String name
) {
    public JobPositionUpdateRequest {
        name = name == null ? null : name.trim();
    }
}

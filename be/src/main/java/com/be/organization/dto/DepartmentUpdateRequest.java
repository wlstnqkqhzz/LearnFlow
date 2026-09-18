package com.be.organization.dto;

import jakarta.validation.constraints.*;

// 부서 수정 입력 - 상위 부서 ID가 null이면 최상위로 이동
public record DepartmentUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Positive Long parentDepartmentId
) {
    public DepartmentUpdateRequest {
        name = name == null ? null : name.trim();
    }
}

package com.be.organization.dto;

import jakarta.validation.constraints.*;

// 부서 생성 입력 - 코드는 생성 시에만 지정
public record DepartmentCreateRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @Positive Long parentDepartmentId
) {
    public DepartmentCreateRequest {
        code = code == null ? null : code.trim();
        name = name == null ? null : name.trim();
    }
}

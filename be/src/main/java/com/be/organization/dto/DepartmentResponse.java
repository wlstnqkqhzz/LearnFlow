package com.be.organization.dto;

import com.be.organization.entity.Department;
import java.time.LocalDateTime;

// 부서 응답 - Entity 및 지연 로딩 프록시를 노출하지 않음
public record DepartmentResponse(
        Long id, String code, String name, Long parentDepartmentId, boolean isActive,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static DepartmentResponse from(Department entity) {
        return new DepartmentResponse(entity.getId(), entity.getCode(), entity.getName(),
                entity.getParentDepartment() == null ? null : entity.getParentDepartment().getId(),
                entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}

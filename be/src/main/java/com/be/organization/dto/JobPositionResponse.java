package com.be.organization.dto;

import com.be.organization.entity.JobPosition;
import java.time.LocalDateTime;

// 직무 응답 - Entity 및 지연 로딩 프록시를 노출하지 않음
public record JobPositionResponse(
        Long id, String code, String name, boolean isActive,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static JobPositionResponse from(JobPosition entity) {
        return new JobPositionResponse(entity.getId(), entity.getCode(), entity.getName(),
                entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}

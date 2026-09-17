package com.be.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

// 생성·수정 시각을 UTC로 관리하는 공통 Entity

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    // 생성 시각 (UTC)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;

    // 최종 수정 시각 (UTC)
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime updatedAt;

    // 최초 저장 시 생성 시각과 수정 시각을 함께 기록
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    // 엔티티 수정 시 최종 수정 시각 갱신
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}

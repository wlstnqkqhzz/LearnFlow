package com.be.global.dto;

import jakarta.validation.constraints.NotNull;

// 조직 활성 상태 변경 요청
public record ActivationUpdateRequest(@NotNull Boolean active) {
}

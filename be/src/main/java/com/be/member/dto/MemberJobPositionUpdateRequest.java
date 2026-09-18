package com.be.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 직원의 직무 변경 입력
public record MemberJobPositionUpdateRequest(@NotNull @Positive Long jobPositionId) {
}

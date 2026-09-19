package com.be.assignment.dto;

import jakarta.validation.constraints.NotNull;

// 물리 삭제 없이 규칙의 활성 여부 변경
public record AssignmentRuleStatusRequest(@NotNull Boolean active) {}

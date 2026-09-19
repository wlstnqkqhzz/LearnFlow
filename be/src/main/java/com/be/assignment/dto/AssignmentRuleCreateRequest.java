package com.be.assignment.dto;

import com.be.assignment.enums.AssignmentRuleType;
import jakarta.validation.constraints.*;

// 생성 시 active 생략은 true, 대상 조합은 Service에서 추가 검증
public record AssignmentRuleCreateRequest(@NotNull AssignmentRuleType ruleType,
        @Positive Long departmentId, @Positive Long jobPositionId,
        @Min(1) @Max(32767) Integer newEmployeeDays, Boolean active) {
    public AssignmentRuleCreateRequest {
        active = active == null ? Boolean.TRUE : active;
    }
}

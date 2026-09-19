package com.be.assignment.dto;

import com.be.assignment.enums.AssignmentRuleType;
import jakarta.validation.constraints.*;

// 규칙 조건 전체 교체: 유형은 필수, 해당하지 않는 대상 필드는 null (활성 여부는 별도 API)
public record AssignmentRuleUpdateRequest(@NotNull AssignmentRuleType ruleType,
        @Positive Long departmentId, @Positive Long jobPositionId,
        @Min(1) @Max(32767) Integer newEmployeeDays) {}

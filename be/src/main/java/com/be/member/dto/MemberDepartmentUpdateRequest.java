package com.be.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 직원의 부서 변경 입력
public record MemberDepartmentUpdateRequest(@NotNull @Positive Long departmentId) {
}

package com.be.member.dto;

import com.be.member.enums.Role;
import jakarta.validation.constraints.NotNull;

// 회원 역할 추가 또는 제거 입력
public record MemberRoleUpdateRequest(@NotNull Role role) {
}

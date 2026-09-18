package com.be.member.dto;

import com.be.member.enums.MemberStatus;
import jakarta.validation.constraints.NotNull;

// 회원 상태 변경 입력
public record MemberStatusUpdateRequest(@NotNull MemberStatus status) {
}

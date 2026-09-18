package com.be.member.dto;

import com.be.member.enums.MemberStatus;
import jakarta.validation.constraints.*;

// 직원 검색 조건 - 페이지 번호는 0부터, 정렬은 회원 ID 오름차순
public record MemberSearchRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        @Size(max = 100) String name,
        @Positive Long departmentId,
        @Positive Long jobPositionId,
        MemberStatus status
) {
    public MemberSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        name = name == null || name.isBlank() ? null : name.trim();
    }
}

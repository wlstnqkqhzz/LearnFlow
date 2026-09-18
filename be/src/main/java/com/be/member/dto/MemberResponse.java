package com.be.member.dto;

import com.be.member.entity.Member;
import com.be.member.enums.MemberStatus;
import com.be.member.enums.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

// 회원 응답 - 원문 비밀번호와 passwordHash는 모두 제외
public record MemberResponse(
        Long id, String employeeNumber, String email, String name,
        Long departmentId, Long jobPositionId, MemberStatus status,
        LocalDate hireDate, LocalDateTime resignedAt, Set<Role> roles,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public MemberResponse {
        roles = Set.copyOf(roles);
    }

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmployeeNumber(),
                member.getEmail(), member.getName(), member.getDepartment().getId(),
                member.getJobPosition().getId(), member.getStatus(), member.getHireDate(),
                member.getResignedAt(), member.getRoles(), member.getCreatedAt(), member.getUpdatedAt());
    }
}

package com.be.assignment.dto;

import com.be.assignment.entity.AssignmentRule;
import com.be.assignment.enums.AssignmentRuleType;
import java.time.LocalDateTime;

// 소속 및 조건만 반환하고 연관 엔티티는 노출하지 않음
public record AssignmentRuleResponse(Long id, Long courseId, AssignmentRuleType ruleType,
        Long departmentId, Long jobPositionId, Short newEmployeeDays, boolean active,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AssignmentRuleResponse from(AssignmentRule rule) {
        return new AssignmentRuleResponse(rule.getId(), rule.getCourse().getId(), rule.getRuleType(),
                rule.getDepartment() == null ? null : rule.getDepartment().getId(),
                rule.getJobPosition() == null ? null : rule.getJobPosition().getId(),
                rule.getNewEmployeeDays(), rule.isActive(), rule.getCreatedAt(), rule.getUpdatedAt());
    }
}

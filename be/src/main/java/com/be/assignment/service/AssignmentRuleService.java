package com.be.assignment.service;

import com.be.assignment.dto.*;
import com.be.assignment.entity.AssignmentRule;
import com.be.assignment.enums.AssignmentRuleType;
import com.be.assignment.repository.AssignmentRuleRepository;
import com.be.course.entity.Course;
import com.be.course.repository.CourseRepository;
import com.be.global.exception.*;
import com.be.organization.entity.*;
import com.be.organization.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 규칙 조건 및 활성 여부 관리: 과거 배정은 수정/삭제하지 않음
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentRuleService {
    private final AssignmentRuleRepository rules;
    private final CourseRepository courses;
    private final DepartmentRepository departments;
    private final JobPositionRepository positions;
    private final AutoAssignmentService autoAssignment;

    @Transactional
    public AssignmentRuleResponse create(@NotNull @Positive Long courseId,
                                          @NotNull @Valid AssignmentRuleCreateRequest request) {
        Course course = lockedCourse(courseId);
        Target target = resolve(request.ruleType(), request.departmentId(), request.jobPositionId(), request.newEmployeeDays());
        AssignmentRule rule = AssignmentRule.create(course, request.ruleType(), target.department(),
                target.position(), target.days(), request.active());
        rules.saveAndFlush(rule);
        if (rule.isActive()) autoAssignment.assignRule(courseId, rule.getId());
        return AssignmentRuleResponse.from(rule);
    }

    public List<AssignmentRuleResponse> getAll(@NotNull @Positive Long courseId) {
        if (!courses.existsById(courseId)) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        return rules.findByCourseIdOrderByIdAsc(courseId).stream().map(AssignmentRuleResponse::from).toList();
    }

    public AssignmentRuleResponse get(@NotNull @Positive Long courseId, @NotNull @Positive Long ruleId) {
        return AssignmentRuleResponse.from(rules.findByIdAndCourseId(ruleId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_RULE_NOT_FOUND)));
    }

    // 조건 전체를 교체하며 활성 규칙이면 새 대상에 대해서만 추가 배정
    @Transactional
    public AssignmentRuleResponse update(@NotNull @Positive Long courseId, @NotNull @Positive Long ruleId,
                                          @NotNull @Valid AssignmentRuleUpdateRequest request) {
        lockedCourse(courseId);
        AssignmentRule rule = lockedRule(courseId, ruleId);
        Target target = resolve(request.ruleType(), request.departmentId(), request.jobPositionId(), request.newEmployeeDays());
        rule.updateTarget(request.ruleType(), target.department(), target.position(), target.days());
        rules.flush();
        if (rule.isActive()) autoAssignment.assignRule(courseId, ruleId);
        return AssignmentRuleResponse.from(rule);
    }

    @Transactional
    public AssignmentRuleResponse changeStatus(@NotNull @Positive Long courseId, @NotNull @Positive Long ruleId,
                                                @NotNull @Valid AssignmentRuleStatusRequest request) {
        lockedCourse(courseId);
        AssignmentRule rule = lockedRule(courseId, ruleId);
        if (request.active()) {
            resolve(rule.getRuleType(), rule.getDepartment() == null ? null : rule.getDepartment().getId(),
                    rule.getJobPosition() == null ? null : rule.getJobPosition().getId(),
                    rule.getNewEmployeeDays() == null ? null : rule.getNewEmployeeDays().intValue());
        }
        rule.changeActive(request.active());
        rules.flush();
        // true 재요청도 멱등적으로 미배정 대상만 추가
        if (rule.isActive()) autoAssignment.assignRule(courseId, ruleId);
        return AssignmentRuleResponse.from(rule);
    }

    private Course lockedCourse(Long id) {
        return courses.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
    }

    private AssignmentRule lockedRule(Long courseId, Long id) {
        return rules.findForUpdate(courseId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_RULE_NOT_FOUND));
    }

    // DB CHECK와 동일한 조합 및 기존 SMALLINT 범위를 Service에서도 검증
    private Target resolve(AssignmentRuleType type, Long departmentId, Long positionId, Integer days) {
        if (days != null && (days <= 0 || days > Short.MAX_VALUE)) {
            throw new BusinessException(ErrorCode.INVALID_NEW_EMPLOYEE_DAYS);
        }
        boolean valid = type != null && switch (type) {
            case ALL_EMPLOYEES -> departmentId == null && positionId == null && days == null;
            case DEPARTMENT -> departmentId != null && positionId == null && days == null;
            case JOB_POSITION -> positionId != null && departmentId == null && days == null;
            case NEW_EMPLOYEE -> days != null && departmentId == null && positionId == null;
        };
        if (!valid) throw new BusinessException(ErrorCode.INVALID_ASSIGNMENT_RULE_TARGET);
        Department department = null;
        JobPosition position = null;
        if (departmentId != null) {
            department = departments.findById(departmentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
            if (!department.isActive()) throw new BusinessException(ErrorCode.INACTIVE_DEPARTMENT);
        }
        if (positionId != null) {
            position = positions.findById(positionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSITION_NOT_FOUND));
            if (!position.isActive()) throw new BusinessException(ErrorCode.INACTIVE_JOB_POSITION);
        }
        return new Target(department, position, days == null ? null : days.shortValue());
    }

    private record Target(Department department, JobPosition position, Short days) {}
}

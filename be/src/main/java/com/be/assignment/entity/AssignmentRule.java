package com.be.assignment.entity;

import com.be.assignment.enums.AssignmentRuleType;
import com.be.course.entity.Course;
import com.be.global.entity.BaseTimeEntity;
import com.be.organization.entity.Department;
import com.be.organization.entity.JobPosition;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 자동 배정 규칙 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "assignment_rules",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        indexes = {
                @Index(name = "idx_assignment_rules_course_active", columnList = "course_id, is_active"),
                @Index(name = "idx_assignment_rules_department_active", columnList = "department_id, is_active"),
                @Index(name = "idx_assignment_rules_job_active", columnList = "job_position_id, is_active"),
                @Index(name = "idx_assignment_rules_type_active", columnList = "rule_type, is_active")
        },
        check = {
                @CheckConstraint(name = "chk_assignment_rules_is_active",
                        constraint = "is_active IN (0, 1)"),
                @CheckConstraint(name = "chk_assignment_rules_target",
                        constraint = "(rule_type = 'ALL_EMPLOYEES' AND department_id IS NULL AND job_position_id IS NULL AND new_employee_days IS NULL) OR (rule_type = 'DEPARTMENT' AND department_id IS NOT NULL AND job_position_id IS NULL AND new_employee_days IS NULL) OR (rule_type = 'JOB_POSITION' AND department_id IS NULL AND job_position_id IS NOT NULL AND new_employee_days IS NULL) OR (rule_type = 'NEW_EMPLOYEE' AND department_id IS NULL AND job_position_id IS NULL AND new_employee_days IS NOT NULL AND new_employee_days > 0)"),
                @CheckConstraint(name = "chk_assignment_rules_rule_type",
                        constraint = "CAST(rule_type AS BINARY) IN ('ALL_EMPLOYEES', 'DEPARTMENT', 'JOB_POSITION', 'NEW_EMPLOYEE')")
        })
public class AssignmentRule extends BaseTimeEntity {

    // 자동 배정 규칙 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 소속 교육과정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_assignment_rules_course", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Course course;

    // 자동 배정 조건 유형
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "rule_type", nullable = false, length = 30)
    private AssignmentRuleType ruleType;

    // 대상 부서 (DEPARTMENT 규칙에서만 지정)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "department_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_assignment_rules_department", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Department department;

    // 대상 직무 (JOB_POSITION 규칙에서만 지정)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "job_position_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_assignment_rules_job_position", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private JobPosition jobPosition;

    // 신입 대상 기간 - 입사 후 경과 일수가 이 값 미만인 직원
    @Column(name = "new_employee_days", nullable = true)
    private Short newEmployeeDays;

    // 자동 배정 규칙 활성 여부
    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false, columnDefinition = "boolean")
    private boolean isActive = true;

    // Service에서 검증한 조건으로 규칙 생성 (소속 과정은 이후 변경 불가)
    public static AssignmentRule create(Course course, AssignmentRuleType type, Department department,
                                        JobPosition position, Short days, boolean active) {
        AssignmentRule rule = new AssignmentRule();
        rule.course = course;
        rule.updateTarget(type, department, position, days);
        rule.changeActive(active);
        return rule;
    }

    // 기존 Enrollment의 최초 규칙 참조는 그대로 두고 향후 배정 조건만 변경
    public void updateTarget(AssignmentRuleType type, Department department, JobPosition position, Short days) {
        this.ruleType = type;
        this.department = department;
        this.jobPosition = position;
        this.newEmployeeDays = days;
    }

    // 비활성화해도 기존 배정은 취소하지 않음
    public void changeActive(boolean active) {
        this.isActive = active;
    }
}

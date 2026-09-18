package com.be.assignment.enums;

// 자동 배정 규칙 유형 Enum

public enum AssignmentRuleType {
    ALL_EMPLOYEES,          // 전체 재직자 대상
    DEPARTMENT,             // 지정 부서의 직접 소속 직원 대상
    JOB_POSITION,           // 지정 직무 직원 대상
    NEW_EMPLOYEE            // 입사 후 지정 기간 이내의 신입 직원 대상
}
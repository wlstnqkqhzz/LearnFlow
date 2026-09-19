package com.be.global.exception;

// Service와 Entity에서 공통으로 사용하는 업무 오류
public enum ErrorCode {
    DEPARTMENT_NOT_FOUND("부서를 찾을 수 없습니다."),
    JOB_POSITION_NOT_FOUND("직무를 찾을 수 없습니다."),
    MEMBER_NOT_FOUND("회원을 찾을 수 없습니다."),
    DUPLICATE_DEPARTMENT_CODE("이미 사용 중인 부서 코드입니다."),
    DUPLICATE_JOB_POSITION_CODE("이미 사용 중인 직무 코드입니다."),
    DUPLICATE_EMPLOYEE_NUMBER("이미 사용 중인 사번입니다."),
    DUPLICATE_EMAIL("이미 사용 중인 이메일입니다."),
    INACTIVE_DEPARTMENT("비활성 부서를 지정할 수 없습니다."),
    INACTIVE_JOB_POSITION("비활성 직무를 지정할 수 없습니다."),
    SELF_PARENT_DEPARTMENT("자기 자신을 상위 부서로 지정할 수 없습니다."),
    DEPARTMENT_CYCLE("부서 계층에 순환 참조가 발생합니다."),
    INVALID_MEMBER_STATUS_TRANSITION("허용되지 않은 회원 상태 전이입니다."),
    RESIGNED_MEMBER_UPDATE("퇴사자의 일반 정보는 수정할 수 없습니다."),
    REQUIRED_EMPLOYEE_ROLE("EMPLOYEE 역할은 제거할 수 없습니다."),
    COURSE_NOT_FOUND("교육과정을 찾을 수 없습니다."),
    COURSE_CONTENT_NOT_FOUND("해당 교육과정의 콘텐츠를 찾을 수 없습니다."),
    INVALID_COURSE_INSTRUCTOR("지정한 회원은 INSTRUCTOR 역할이 없습니다."),
    INVALID_COURSE_PERIOD("교육 시작일은 종료일보다 늦을 수 없습니다."),
    COURSE_DATES_REQUIRED("공개·종료 교육과정에는 시작일과 종료일이 필요합니다."),
    INVALID_COURSE_STATUS_TRANSITION("허용되지 않은 교육과정 상태 전이입니다."),
    DUPLICATE_CONTENT_SORT_ORDER("이미 사용 중인 콘텐츠 순서입니다."),
    INVALID_CONTENT_ORDER("현재 교육과정의 전체 콘텐츠 ID를 중복 없이 지정해야 합니다."),
    CONTENT_ORDER_LIMIT_EXCEEDED("콘텐츠 순서 값이 재정렬 가능한 범위를 초과했습니다."),
    ASSIGNMENT_RULE_NOT_FOUND("해당 교육과정의 배정 규칙을 찾을 수 없습니다."),
    INVALID_ASSIGNMENT_RULE_TARGET("배정 규칙 유형과 대상 조건이 일치하지 않습니다."),
    INVALID_NEW_EMPLOYEE_DAYS("신입 대상 기간은 1~32767일이어야 합니다."),
    ENROLLMENT_NOT_FOUND("교육 배정 내역을 찾을 수 없습니다."),
    DUPLICATE_ENROLLMENT("이미 해당 직원에게 배정된 교육과정입니다."),
    COURSE_NOT_OPEN_FOR_ASSIGNMENT("OPEN 교육과정에만 신규 배정할 수 있습니다."),
    RESIGNED_MEMBER_ASSIGNMENT("퇴사자에게 신규 교육을 배정할 수 없습니다."),
    ENROLLMENT_PROGRESS_ACCESS_DENIED("해당 수강의 학습 진도에 접근할 수 없습니다."),
    ENROLLMENT_PROGRESS_NOT_EDITABLE("종료된 수강의 학습 진도는 변경할 수 없습니다."),
    INVALID_PROGRESS_RATE("진도율은 소수점 둘째 자리까지의 0~100 값이어야 합니다.");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}

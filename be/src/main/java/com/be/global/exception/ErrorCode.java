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
    REQUIRED_EMPLOYEE_ROLE("EMPLOYEE 역할은 제거할 수 없습니다.");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}

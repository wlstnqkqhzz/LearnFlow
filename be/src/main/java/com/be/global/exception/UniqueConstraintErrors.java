package com.be.global.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

// 사전 중복 검사 이후 동시 요청으로 발생한 DB UNIQUE 오류도 업무 오류로 변환
public final class UniqueConstraintErrors {
    private UniqueConstraintErrors() {
    }

    public static RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                String name = violation.getConstraintName().replace("`", "");
                name = name.substring(name.lastIndexOf('.') + 1);
                ErrorCode code = switch (name) {
                    case "uk_departments_code" -> ErrorCode.DUPLICATE_DEPARTMENT_CODE;
                    case "uk_job_positions_code" -> ErrorCode.DUPLICATE_JOB_POSITION_CODE;
                    case "uk_members_employee_number" -> ErrorCode.DUPLICATE_EMPLOYEE_NUMBER;
                    case "uk_members_email" -> ErrorCode.DUPLICATE_EMAIL;
                    case "uk_course_contents_course_sort_order" -> ErrorCode.DUPLICATE_CONTENT_SORT_ORDER;
                    case "uk_enrollments_member_course" -> ErrorCode.DUPLICATE_ENROLLMENT;
                    case "uk_exams_course" -> ErrorCode.DUPLICATE_EXAM;
                    case "uk_questions_exam_sort_order" -> ErrorCode.DUPLICATE_QUESTION_SORT_ORDER;
                    case "uk_question_choices_question_sort_order" -> ErrorCode.DUPLICATE_CHOICE_SORT_ORDER;
                    default -> null;
                };
                if (code != null) {
                    return new BusinessException(code);
                }
            }
        }
        // FK, CHECK 등 다른 무결성 오류를 중복 오류로 오인하지 않음
        return exception;
    }
}

package com.be.global.exception;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;

// 동시 중복 요청의 DB UNIQUE 예외를 구분하고 다른 오류는 보존
class UniqueConstraintErrorsTest {
    @ParameterizedTest
    @CsvSource({
            "uk_departments_code, DUPLICATE_DEPARTMENT_CODE",
            "uk_job_positions_code, DUPLICATE_JOB_POSITION_CODE",
            "members.uk_members_employee_number, DUPLICATE_EMPLOYEE_NUMBER",
            "members.uk_members_email, DUPLICATE_EMAIL",
            "uk_course_contents_course_sort_order, DUPLICATE_CONTENT_SORT_ORDER",
            "uk_enrollments_member_course, DUPLICATE_ENROLLMENT",
            "uk_exams_course, DUPLICATE_EXAM",
            "uk_questions_exam_sort_order, DUPLICATE_QUESTION_SORT_ORDER",
            "uk_question_choices_question_sort_order, DUPLICATE_CHOICE_SORT_ORDER"
    })
    void translatesKnownConstraint(String name, ErrorCode code) {
        var exception = new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException(), name));
        assertThat(UniqueConstraintErrors.translate(exception))
                .isInstanceOfSatisfying(BusinessException.class,
                        business -> assertThat(business.getErrorCode()).isEqualTo(code));
    }

    @Test
    void preservesUnknownIntegrityViolation() {
        var exception = new DataIntegrityViolationException("foreign key");
        assertThat(UniqueConstraintErrors.translate(exception)).isSameAs(exception);
    }
}

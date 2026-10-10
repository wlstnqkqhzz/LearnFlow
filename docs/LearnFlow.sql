-- ============================================================
-- LearnFlow schema.sql
--
-- Target: MySQL 8.0.16+
-- Storage engine: InnoDB
-- Charset: utf8mb4
-- Collation: utf8mb4_unicode_ci
--
-- 데이터베이스가 없으면 생성하고, 이후 모든 테이블을 learnflow에 생성한다.
-- 주의: 전체 실행 시 아래 22개 테이블과 기존 데이터를 삭제하고 다시 생성한다.
-- 개발용 초기화 스크립트이며, 삭제한 데이터는 백업 없이는 복구할 수 없다.
-- DATETIME(6)은 애플리케이션에서 UTC로 기록한다.
-- created_at / updated_at은 애플리케이션에서 관리한다.
-- ============================================================

CREATE DATABASE IF NOT EXISTS learnflow
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE learnflow;

-- ============================================================
-- 0. 기존 테이블 삭제 (FK 의존관계의 역순)
-- FOREIGN_KEY_CHECKS를 비활성화하지 않고 자식 테이블부터 삭제한다.
-- ============================================================

-- 재교육 정책의 원본 Course 참조와 회차 Course 참조 간 FK 순환을 먼저 해제한다.
SET @retraining_drop_fk = IF(EXISTS(
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'courses'
      AND CONSTRAINT_NAME = 'fk_courses_retraining'),
    'ALTER TABLE courses DROP FOREIGN KEY fk_courses_retraining', 'SELECT 1');
PREPARE retraining_drop_statement FROM @retraining_drop_fk;
EXECUTE retraining_drop_statement;
DEALLOCATE PREPARE retraining_drop_statement;
DROP TABLE IF EXISTS retraining_policies;
DROP TABLE IF EXISTS certificates;
DROP TABLE IF EXISTS mobile_push_subscriptions;
DROP TABLE IF EXISTS push_subscriptions;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS assignment_submissions;
DROP TABLE IF EXISTS assignments;
DROP TABLE IF EXISTS exam_answer_choices;
DROP TABLE IF EXISTS exam_answers;
DROP TABLE IF EXISTS exam_attempts;
DROP TABLE IF EXISTS question_choices;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS exams;
DROP TABLE IF EXISTS content_progresses;
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS assignment_rules;
DROP TABLE IF EXISTS course_contents;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS member_roles;
DROP TABLE IF EXISTS members;
DROP TABLE IF EXISTS job_positions;
DROP TABLE IF EXISTS departments;

-- ============================================================
-- 1. departments
-- ============================================================

CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    parent_department_id BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_departments_code
        UNIQUE (code),

    INDEX idx_departments_parent_name
        (parent_department_id, name),

    CONSTRAINT fk_departments_parent
        FOREIGN KEY (parent_department_id)
        REFERENCES departments (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_departments_is_active
        CHECK (is_active IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. job_positions
-- ============================================================

CREATE TABLE job_positions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_job_positions_code
        UNIQUE (code),

    CONSTRAINT chk_job_positions_is_active
        CHECK (is_active IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. members
-- ============================================================

CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_number VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    department_id BIGINT NOT NULL,
    job_position_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    hire_date DATE NOT NULL,
    resigned_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_members_employee_number
        UNIQUE (employee_number),

    CONSTRAINT uk_members_email
        UNIQUE (email),

    INDEX idx_members_department_status
        (department_id, status),

    INDEX idx_members_job_position_status
        (job_position_id, status),

    INDEX idx_members_status_hire_date
        (status, hire_date),

    CONSTRAINT fk_members_department
        FOREIGN KEY (department_id)
        REFERENCES departments (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_members_job_position
        FOREIGN KEY (job_position_id)
        REFERENCES job_positions (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_members_status
        CHECK (
            CAST(status AS BINARY) IN (
                'ACTIVE',
                'ON_LEAVE',
                'RESIGNED'
            )
        ),

    CONSTRAINT chk_members_status_resigned_at
        CHECK (
            (
                status IN ('ACTIVE', 'ON_LEAVE')
                AND resigned_at IS NULL
            )
            OR
            (
                status = 'RESIGNED'
                AND resigned_at IS NOT NULL
            )
        )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. member_roles
-- ============================================================

CREATE TABLE member_roles (
    member_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,

    PRIMARY KEY (member_id, role),

    INDEX idx_member_roles_role_member
        (role, member_id),

    CONSTRAINT fk_member_roles_member
        FOREIGN KEY (member_id)
        REFERENCES members (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_member_roles_role
        CHECK (
            CAST(role AS BINARY) IN (
                'EMPLOYEE',
                'INSTRUCTOR',
                'ADMIN'
            )
        )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 5. courses
-- ============================================================

CREATE TABLE courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    course_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    start_date DATE NULL,
    end_date DATE NULL,
    passing_progress_rate DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    instructor_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    INDEX idx_courses_instructor_status
        (instructor_id, status),

    INDEX idx_courses_status_period
        (status, start_date, end_date),

    CONSTRAINT fk_courses_instructor
        FOREIGN KEY (instructor_id)
        REFERENCES members (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_courses_course_type
        CHECK (
            CAST(course_type AS BINARY) IN (
                'MANDATORY',
                'OPTIONAL'
            )
        ),

    CONSTRAINT chk_courses_status
        CHECK (
            CAST(status AS BINARY) IN (
                'DRAFT',
                'OPEN',
                'CLOSED'
            )
        ),

    CONSTRAINT chk_courses_passing_progress_rate
        CHECK (passing_progress_rate BETWEEN 0 AND 100),

    CONSTRAINT chk_courses_period
        CHECK (
            start_date IS NULL
            OR end_date IS NULL
            OR start_date <= end_date
        ),

    CONSTRAINT chk_courses_published_fields
        CHECK (
            status = 'DRAFT'
            OR (
                start_date IS NOT NULL
                AND end_date IS NOT NULL
            )
        )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 6. course_contents
-- ============================================================

CREATE TABLE course_contents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    content_url VARCHAR(2048) NOT NULL,
    duration_seconds INT NULL,
    sort_order INT NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_course_contents_course_sort_order
        UNIQUE (course_id, sort_order),

    CONSTRAINT fk_course_contents_course
        FOREIGN KEY (course_id)
        REFERENCES courses (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_course_contents_content_type
        CHECK (
            CAST(content_type AS BINARY) IN (
                'VIDEO',
                'DOCUMENT',
                'LINK'
            )
        ),

    CONSTRAINT chk_course_contents_duration_seconds
        CHECK (
            duration_seconds IS NULL
            OR duration_seconds >= 0
        ),

    CONSTRAINT chk_course_contents_sort_order
        CHECK (sort_order > 0),

    CONSTRAINT chk_course_contents_is_required
        CHECK (is_required IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 7. assignment_rules
-- ============================================================

CREATE TABLE assignment_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    rule_type VARCHAR(30) NOT NULL,
    department_id BIGINT NULL,
    job_position_id BIGINT NULL,
    new_employee_days SMALLINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    INDEX idx_assignment_rules_course_active
        (course_id, is_active),

    INDEX idx_assignment_rules_department_active
        (department_id, is_active),

    INDEX idx_assignment_rules_job_active
        (job_position_id, is_active),

    INDEX idx_assignment_rules_type_active
        (rule_type, is_active),

    CONSTRAINT fk_assignment_rules_course
        FOREIGN KEY (course_id)
        REFERENCES courses (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_assignment_rules_department
        FOREIGN KEY (department_id)
        REFERENCES departments (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_assignment_rules_job_position
        FOREIGN KEY (job_position_id)
        REFERENCES job_positions (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_assignment_rules_rule_type
        CHECK (
            CAST(rule_type AS BINARY) IN (
                'ALL_EMPLOYEES',
                'DEPARTMENT',
                'JOB_POSITION',
                'NEW_EMPLOYEE'
            )
        ),

    CONSTRAINT chk_assignment_rules_is_active
        CHECK (is_active IN (0, 1)),

    CONSTRAINT chk_assignment_rules_target
        CHECK (
            (
                rule_type = 'ALL_EMPLOYEES'
                AND department_id IS NULL
                AND job_position_id IS NULL
                AND new_employee_days IS NULL
            )
            OR
            (
                rule_type = 'DEPARTMENT'
                AND department_id IS NOT NULL
                AND job_position_id IS NULL
                AND new_employee_days IS NULL
            )
            OR
            (
                rule_type = 'JOB_POSITION'
                AND department_id IS NULL
                AND job_position_id IS NOT NULL
                AND new_employee_days IS NULL
            )
            OR
            (
                rule_type = 'NEW_EMPLOYEE'
                AND department_id IS NULL
                AND job_position_id IS NULL
                AND new_employee_days IS NOT NULL
                AND new_employee_days > 0
            )
        )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 8. enrollments
-- ============================================================

CREATE TABLE enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    assignment_source VARCHAR(20) NOT NULL,
    assignment_rule_id BIGINT NULL,
    assigned_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    due_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_enrollments_member_course
        UNIQUE (member_id, course_id),

    INDEX idx_enrollments_member_status_due
        (member_id, status, due_date),

    INDEX idx_enrollments_course_status
        (course_id, status),

    INDEX idx_enrollments_status_due
        (status, due_date),

    INDEX idx_enrollments_assignment_rule
        (assignment_rule_id),

    CONSTRAINT fk_enrollments_member
        FOREIGN KEY (member_id)
        REFERENCES members (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_enrollments_course
        FOREIGN KEY (course_id)
        REFERENCES courses (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_enrollments_assignment_rule
        FOREIGN KEY (assignment_rule_id)
        REFERENCES assignment_rules (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_enrollments_status
        CHECK (
            CAST(status AS BINARY) IN (
                'ASSIGNED',
                'IN_PROGRESS',
                'COMPLETED',
                'FAILED',
                'EXPIRED'
            )
        ),

    CONSTRAINT chk_enrollments_assignment_source
        CHECK (
            CAST(assignment_source AS BINARY) IN (
                'MANUAL',
                'AUTOMATIC'
            )
        ),

    CONSTRAINT chk_enrollments_assignment_source_rule
        CHECK (
            (
                assignment_source = 'MANUAL'
                AND assignment_rule_id IS NULL
            )
            OR
            (
                assignment_source = 'AUTOMATIC'
                AND assignment_rule_id IS NOT NULL
            )
        ),

    CONSTRAINT chk_enrollments_status_timestamps
        CHECK (
            (
                status = 'ASSIGNED'
                AND started_at IS NULL
                AND completed_at IS NULL
            )
            OR
            (
                status = 'IN_PROGRESS'
                AND started_at IS NOT NULL
                AND completed_at IS NULL
            )
            OR
            (
                status = 'COMPLETED'
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
            )
            OR
            (
                status = 'FAILED'
                AND started_at IS NOT NULL
                AND completed_at IS NULL
            )
            OR
            (
                status = 'EXPIRED'
                AND completed_at IS NULL
            )
        ),

    CONSTRAINT chk_enrollments_started_at
        CHECK (
            started_at IS NULL
            OR started_at >= assigned_at
        ),

    CONSTRAINT chk_enrollments_completed_at
        CHECK (
            completed_at IS NULL
            OR completed_at >= started_at
        ),

    CONSTRAINT chk_enrollments_version
        CHECK (version >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 9. content_progresses
-- ============================================================

CREATE TABLE content_progresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    enrollment_id BIGINT NOT NULL,
    course_content_id BIGINT NOT NULL,
    progress_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_content_progresses_enrollment_content
        UNIQUE (enrollment_id, course_content_id),

    INDEX idx_content_progresses_content
        (course_content_id),

    CONSTRAINT fk_content_progresses_enrollment
        FOREIGN KEY (enrollment_id)
        REFERENCES enrollments (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_content_progresses_content
        FOREIGN KEY (course_content_id)
        REFERENCES course_contents (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_content_progresses_progress_rate
        CHECK (progress_rate BETWEEN 0 AND 100),

    CONSTRAINT chk_content_progresses_completed_at
        CHECK (
            (
                progress_rate = 100
                AND completed_at IS NOT NULL
            )
            OR
            (
                progress_rate < 100
                AND completed_at IS NULL
            )
        ),

    CONSTRAINT chk_content_progresses_version
        CHECK (version >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 10. exams
-- ============================================================

CREATE TABLE exams (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    passing_score DECIMAL(5,2) NOT NULL,
    max_attempts INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_exams_course
        UNIQUE (course_id),

    CONSTRAINT fk_exams_course
        FOREIGN KEY (course_id)
        REFERENCES courses (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_exams_passing_score
        CHECK (passing_score BETWEEN 0 AND 100),

    CONSTRAINT chk_exams_max_attempts
        CHECK (max_attempts > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 11. questions
-- ============================================================

CREATE TABLE questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    score DECIMAL(7,2) NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_questions_exam_sort_order
        UNIQUE (exam_id, sort_order),

    CONSTRAINT fk_questions_exam
        FOREIGN KEY (exam_id)
        REFERENCES exams (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_questions_question_type
        CHECK (
            CAST(question_type AS BINARY) IN (
                'SINGLE_CHOICE',
                'MULTIPLE_CHOICE',
                'TRUE_FALSE'
            )
        ),

    CONSTRAINT chk_questions_score
        CHECK (score > 0),

    CONSTRAINT chk_questions_sort_order
        CHECK (sort_order > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 12. question_choices
-- ============================================================

CREATE TABLE question_choices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    choice_text VARCHAR(1000) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_question_choices_question_sort_order
        UNIQUE (question_id, sort_order),

    CONSTRAINT fk_question_choices_question
        FOREIGN KEY (question_id)
        REFERENCES questions (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_question_choices_is_correct
        CHECK (is_correct IN (0, 1)),

    CONSTRAINT chk_question_choices_sort_order
        CHECK (sort_order > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 13. exam_attempts
-- ============================================================

CREATE TABLE exam_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    score DECIMAL(5,2) NULL,
    passed BOOLEAN NULL,
    started_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_exam_attempts_enrollment_exam_number
        UNIQUE (enrollment_id, exam_id, attempt_number),

    INDEX idx_exam_attempts_exam_submitted
        (exam_id, submitted_at),

    CONSTRAINT fk_exam_attempts_exam
        FOREIGN KEY (exam_id)
        REFERENCES exams (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_exam_attempts_enrollment
        FOREIGN KEY (enrollment_id)
        REFERENCES enrollments (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_exam_attempts_attempt_number
        CHECK (attempt_number > 0),

    CONSTRAINT chk_exam_attempts_submission
        CHECK (
            (
                submitted_at IS NULL
                AND score IS NULL
                AND passed IS NULL
            )
            OR
            (
                submitted_at IS NOT NULL
                AND submitted_at >= started_at
                AND score IS NOT NULL
                AND score BETWEEN 0 AND 100
                AND passed IS NOT NULL
                AND passed IN (0, 1)
            )
        )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 14. exam_answers
-- ============================================================

CREATE TABLE exam_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    is_correct BOOLEAN NULL,
    earned_score DECIMAL(7,2) NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_exam_answers_attempt_question
        UNIQUE (exam_attempt_id, question_id),

    INDEX idx_exam_answers_question
        (question_id),

    CONSTRAINT fk_exam_answers_attempt
        FOREIGN KEY (exam_attempt_id)
        REFERENCES exam_attempts (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_exam_answers_question
        FOREIGN KEY (question_id)
        REFERENCES questions (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_exam_answers_grading
        CHECK (
            (
                is_correct IS NULL
                AND earned_score IS NULL
            )
            OR
            (
                is_correct IS NOT NULL
                AND is_correct IN (0, 1)
                AND earned_score IS NOT NULL
                AND earned_score >= 0
            )
        )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 15. exam_answer_choices
-- ============================================================

CREATE TABLE exam_answer_choices (
    exam_answer_id BIGINT NOT NULL,
    question_choice_id BIGINT NOT NULL,

    PRIMARY KEY (exam_answer_id, question_choice_id),

    INDEX idx_exam_answer_choices_question_choice
        (question_choice_id),

    CONSTRAINT fk_exam_answer_choices_answer
        FOREIGN KEY (exam_answer_id)
        REFERENCES exam_answers (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_exam_answer_choices_question_choice
        FOREIGN KEY (question_choice_id)
        REFERENCES question_choices (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 16. notifications (개인 교육 알림)
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_enrollment_type UNIQUE (enrollment_id, type),
    INDEX idx_notifications_member_created (member_id, created_at, id),
    INDEX idx_notifications_member_read (member_id, read_at),
    CONSTRAINT fk_notifications_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_notifications_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_notifications_type CHECK (CAST(type AS BINARY) IN ('ENROLLMENT_ASSIGNED', 'COURSE_COMPLETED', 'COURSE_FAILED', 'ENROLLMENT_EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. assignments / 18. assignment_submissions (과제 및 최신 제출물)
CREATE TABLE assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    is_required BOOLEAN NOT NULL,
    due_date DATE NOT NULL,
    passing_score DECIMAL(5,2) NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_assignments_course_sort_order UNIQUE (course_id, sort_order),
    INDEX idx_assignments_course_required_due (course_id, is_required, due_date),
    CONSTRAINT fk_assignments_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_assignments_score CHECK (passing_score BETWEEN 0 AND 100),
    CONSTRAINT chk_assignments_order CHECK (sort_order > 0),
    CONSTRAINT chk_assignments_required CHECK (is_required IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assignment_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    submission_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    submission_count INT NOT NULL,
    score DECIMAL(5,2) NULL,
    passed BOOLEAN NULL,
    feedback TEXT NULL,
    graded_at DATETIME(6) NULL,
    graded_by_member_id BIGINT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_assignment_submissions_assignment_enrollment UNIQUE (assignment_id, enrollment_id),
    INDEX idx_submissions_enrollment_passed (enrollment_id, passed, assignment_id),
    INDEX idx_submissions_assignment_graded (assignment_id, graded_at, submitted_at),
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_submissions_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_submissions_grader FOREIGN KEY (graded_by_member_id) REFERENCES members(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_submissions_type CHECK (CAST(submission_type AS BINARY) IN ('TEXT', 'URL')),
    CONSTRAINT chk_submissions_count CHECK (submission_count > 0),
    CONSTRAINT chk_submissions_version CHECK (version >= 0),
    CONSTRAINT chk_submissions_grading CHECK (
        (score IS NULL AND passed IS NULL AND feedback IS NULL AND graded_at IS NULL AND graded_by_member_id IS NULL)
        OR (score IS NOT NULL AND score BETWEEN 0 AND 100 AND passed IS NOT NULL AND passed IN (0, 1)
            AND graded_at IS NOT NULL AND graded_at >= submitted_at AND graded_by_member_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 수료증: 최초 발급 시 스냅샷, Enrollment 1:1. PDF Binary는 저장하지 않는다.
CREATE TABLE certificates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    enrollment_id BIGINT NOT NULL,
    certificate_number VARCHAR(64) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    member_name VARCHAR(100) NOT NULL,
    course_title VARCHAR(200) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_certificates_enrollment UNIQUE (enrollment_id),
    CONSTRAINT uk_certificates_number UNIQUE (certificate_number),
    CONSTRAINT fk_certificates_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_certificates_dates CHECK (issued_at >= completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retraining_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_course_id BIGINT NOT NULL,
    base_title VARCHAR(150) NOT NULL,
    enabled BOOLEAN NOT NULL,
    auto_create BOOLEAN NOT NULL,
    auto_open BOOLEAN NOT NULL,
    interval_months INT NOT NULL,
    first_start_date DATE NOT NULL,
    duration_days INT NOT NULL,
    generation_lead_days INT NOT NULL,
    next_occurrence_number INT NOT NULL,
    next_generation_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_retraining_due (enabled, auto_create, next_generation_date, id),
    CONSTRAINT fk_retraining_source FOREIGN KEY (source_course_id) REFERENCES courses(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_retraining_schedule CHECK (
        interval_months BETWEEN 1 AND 1200 AND duration_days BETWEEN 1 AND 3660
        AND generation_lead_days BETWEEN 0 AND 3660 AND next_occurrence_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE courses
    ADD COLUMN retraining_policy_id BIGINT NULL,
    ADD COLUMN occurrence_number INT NULL,
    ADD CONSTRAINT uk_courses_retraining_occurrence UNIQUE (retraining_policy_id, occurrence_number),
    ADD CONSTRAINT fk_courses_retraining FOREIGN KEY (retraining_policy_id) REFERENCES retraining_policies(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT chk_courses_retraining CHECK (
        (retraining_policy_id IS NULL AND occurrence_number IS NULL)
        OR (retraining_policy_id IS NOT NULL AND occurrence_number IS NOT NULL AND occurrence_number > 0));

-- 모바일 Push는 Web Push와 독립된 설치/소유권/Token을 유지한다.
CREATE TABLE mobile_push_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    installation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    installation_secret_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_generation BIGINT NOT NULL DEFAULT 0, -- legacy installations start at 0; new requests start at 1
    expo_push_token VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    platform VARCHAR(10) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_mobile_push_installation UNIQUE (installation_id),
    CONSTRAINT uk_mobile_push_token UNIQUE (token_hash),
    INDEX idx_mobile_push_member_enabled (member_id, enabled),
    CONSTRAINT fk_mobile_push_member FOREIGN KEY (member_id) REFERENCES members(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_mobile_push_platform CHECK (platform IN ('ANDROID', 'IOS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Web Push 구독: DB Notification 이후의 전달 채널. 키와 endpoint는 외부에 노출하지 않는다.
CREATE TABLE push_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    endpoint_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    p256dh VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    auth VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    enabled BOOLEAN NOT NULL,
    expiration_time DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_endpoint_hash UNIQUE (endpoint_hash),
    INDEX idx_push_member_enabled (member_id, enabled),
    CONSTRAINT fk_push_member FOREIGN KEY (member_id) REFERENCES members(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Assignment Backend additive schema, MySQL 8.0.16+.
-- 기존 LearnFlow DB에 한 번 수동 적용한다. DROP/기존 데이터 변경 없음.
-- 자동 실행/Flyway migration이 아니며, 신규 DB는 docs/LearnFlow.sql을 사용한다.
USE learnflow;

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

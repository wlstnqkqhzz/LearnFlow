-- MySQL 8.0.16+ 기존 DB용 1회 적용 마이그레이션. 기존 행을 UPDATE/DELETE하지 않는다.
-- DDL은 자동 커밋된다. 배포 전 백업 및 중복 적용 여부를 확인한다.
-- docs는 Git 제외 대상이므로 배포 SQL은 Backend에 보관한다.
USE learnflow;

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

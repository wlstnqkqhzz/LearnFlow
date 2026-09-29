-- 기존 MySQL 8.0.16+ DB용 비파괴 추가 스크립트.
-- 기존 테이블/데이터는 변경하지 않는다. DATETIME(6)은 UTC로 기록한다.
USE learnflow;

CREATE TABLE IF NOT EXISTS certificates (
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

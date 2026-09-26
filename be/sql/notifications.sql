-- 기존 LearnFlow DB를 선택한 뒤 한 번 실행하는 추가 DDL. 기존 테이블/데이터를 삭제하지 않는다.
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

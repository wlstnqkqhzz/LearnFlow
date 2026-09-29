package com.be.enrollment;

import com.be.enrollment.repository.EnrollmentRepository;
import com.be.security.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.jpa.show-sql=false", "spring.sql.init.mode=never"})
class EnrollmentProgressLockTest {
    @Autowired EnrollmentRepository enrollments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("jwt.secret", JwtTestSupport::secret);
        r.add("jwt.access-token-ttl-seconds", () -> 300);
        r.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void progressLockDoesNotPropagateToUnversionedCourseOrMemberAndStillIncrementsEnrollment() {
        jdbc.execute("CREATE TABLE courses(id BIGINT PRIMARY KEY,title VARCHAR(200),description TEXT,course_type VARCHAR(20),status VARCHAR(20),start_date DATE,end_date DATE,passing_progress_rate DECIMAL(5,2),instructor_id BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE members(id BIGINT PRIMARY KEY,employee_number VARCHAR(50),email VARCHAR(255),password_hash VARCHAR(255),name VARCHAR(100),department_id BIGINT,job_position_id BIGINT,status VARCHAR(20),hire_date DATE,resigned_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE enrollments(id BIGINT PRIMARY KEY,member_id BIGINT,course_id BIGINT,status VARCHAR(20),assignment_source VARCHAR(20),assignment_rule_id BIGINT,assigned_at TIMESTAMP,started_at TIMESTAMP,completed_at TIMESTAMP,due_date DATE,version BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.update("INSERT INTO courses(id,title,course_type,status,passing_progress_rate) VALUES(1,'course','MANDATORY','OPEN',100)");
        jdbc.update("INSERT INTO members(id,name,status) VALUES(2,'employee','ACTIVE')");
        jdbc.update("INSERT INTO enrollments(id,member_id,course_id,status,assignment_source,assigned_at,due_date,version) VALUES(3,2,1,'IN_PROGRESS','MANUAL',CURRENT_TIMESTAMP,DATE '2026-10-31',0)");
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                var enrollment = enrollments.findForProgressUpdate(3L).orElseThrow();
                // Both associations remain usable inside the existing service transaction.
                assertThat(enrollment.getCourse().getTitle()).isEqualTo("course");
                assertThat(enrollment.getMember().getName()).isEqualTo("employee");
            });
            assertThat(jdbc.queryForObject("SELECT version FROM enrollments WHERE id=3", Long.class)).isEqualTo(1L);
        } finally {
            jdbc.execute("DROP TABLE enrollments");
            jdbc.execute("DROP TABLE members");
            jdbc.execute("DROP TABLE courses");
        }
    }
}

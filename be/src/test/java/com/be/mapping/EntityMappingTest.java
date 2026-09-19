package com.be.mapping;

import com.be.organization.entity.Department;
import com.be.organization.entity.JobPosition;
import com.be.member.entity.Member;
import com.be.course.entity.Course;
import com.be.course.entity.CourseContent;
import com.be.assignment.entity.AssignmentRule;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.entity.ContentProgress;
import com.be.exam.entity.Exam;
import com.be.exam.entity.Question;
import com.be.exam.entity.QuestionChoice;
import com.be.exam.entity.ExamAttempt;
import com.be.exam.entity.ExamAnswer;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// DB 연결 없이 엔티티 매핑과 MySQL DDL 생성을 검증하는 테스트

class EntityMappingTest {

    // 13개 엔티티와 2개 연결 테이블의 스키마 생성 결과 검증
    @Test
    void generatesMysqlSchemaWithoutConnectingToDatabase() {
        // 실제 DB 대신 메모리에 DDL을 생성하도록 설정
        StringWriter script = new StringWriter();
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting("jakarta.persistence.schema-generation.scripts.create-target", script)
                .build();
        try {
            // 검증 대상 엔티티를 Hibernate 메타데이터에 등록
            var sources = new MetadataSources(registry);
            List.of(
                    Department.class,
                    JobPosition.class,
                    Member.class,
                    Course.class,
                    CourseContent.class,
                    AssignmentRule.class,
                    Enrollment.class,
                    ContentProgress.class,
                    Exam.class,
                    Question.class,
                    QuestionChoice.class,
                    ExamAttempt.class,
                    ExamAnswer.class
            ).forEach(sources::addAnnotatedClass);
            var metadata = sources.buildMetadata();
            assertThat(metadata.getEntityBindings()).hasSize(13);
            assertThat(metadata.getCollectionBindings()).hasSize(2);

            // 전체 매핑으로 SessionFactory가 정상 생성되는지 확인
            try (var sessionFactory = metadata.buildSessionFactory()) {
                assertThat(sessionFactory.isOpen()).isTrue();
                // DB 실행 없이 새 교육과정 Repository의 JPQL 경로·타입을 Hibernate로 검증
                try (var session = sessionFactory.openSession()) {
                    for (var method : com.be.course.repository.CourseRepository.class.getDeclaredMethods()) {
                        var query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
                        if (query != null) {
                            assertThat(session.createSelectionQuery(query.value(), Course.class)).isNotNull();
                        }
                    }
                    for (Class<?> repository : List.of(com.be.assignment.repository.AssignmentRuleRepository.class,
                            com.be.enrollment.repository.EnrollmentRepository.class,
                            com.be.member.repository.MemberRepository.class)) {
                        for (var method : repository.getDeclaredMethods()) {
                            var query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
                            if (query != null) assertThat(session.createSelectionQuery(query.value(), Object.class)).isNotNull();
                        }
                    }
                }
            }

            // 테이블 수, 복합 PK, 주요 제약 및 MySQL 저장 옵션 확인
            String ddl = script.toString().toLowerCase(Locale.ROOT);
            assertThat(ddl.split("create table ").length - 1).isEqualTo(15);
            assertThat(ddl).doesNotContain(" enum (", " enum(");
            assertThat(ddl).contains(
                    "primary key (member_id, role)",
                    "primary key (exam_answer_id, question_choice_id)",
                    "uk_enrollments_member_course",
                    "uk_exams_course",
                    "chk_members_status_resigned_at",
                    "chk_assignment_rules_target",
                    "chk_enrollments_status_timestamps",
                    "chk_content_progresses_completed_at",
                    "chk_exam_attempts_submission",
                    "chk_exam_answers_grading",
                    "chk_member_roles_role",
                    "on delete restrict on update restrict",
                    "engine=innodb",
                    "default charset=utf8mb4 collate=utf8mb4_unicode_ci"
            );
            assertThat(ddl.split("auto_increment").length - 1).isEqualTo(13);
        } finally {
            // 테스트에서 사용한 Hibernate 리소스 해제
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}

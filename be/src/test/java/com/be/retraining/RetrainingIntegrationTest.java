package com.be.retraining;

import com.be.assignment.entity.AssignmentRule;
import com.be.assignment.enums.AssignmentRuleType;
import com.be.assignment.repository.AssignmentRuleRepository;
import com.be.assignment.service.*;
import com.be.certificate.entity.Certificate;
import com.be.certificate.repository.CertificateRepository;
import com.be.course.entity.*;
import com.be.course.enums.*;
import com.be.course.repository.*;
import com.be.course.service.CourseService;
import com.be.coursework.entity.Assignment;
import com.be.coursework.repository.AssignmentRepository;
import com.be.coursework.service.AssignmentPolicy;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.exam.entity.*;
import com.be.exam.enums.QuestionType;
import com.be.exam.repository.*;
import com.be.exam.service.ExamConfigurationValidator;
import com.be.global.exception.*;
import com.be.member.repository.MemberRepository;
import com.be.notification.service.NotificationService;
import com.be.retraining.dto.*;
import com.be.retraining.service.*;
import com.be.security.JwtTestSupport;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.jpa.show-sql=false", "spring.sql.init.mode=never"})
@Import({RetrainingPolicyService.class, RetrainingOccurrenceService.class, RetrainingCourseCopier.class,
    RetrainingSchedulerService.class, CourseService.class, AssignmentRuleService.class, AutoAssignmentService.class,
    AssignmentPolicy.class, NotificationService.class, ExamConfigurationValidator.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RetrainingIntegrationTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T15:30:00Z"), ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    @Autowired RetrainingPolicyService policies;
    @Autowired RetrainingOccurrenceService occurrences;
    @Autowired RetrainingSchedulerService scheduler;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CourseRepository courses;
    @Autowired CourseContentRepository contents;
    @Autowired ExamRepository exams;
    @Autowired QuestionRepository questions;
    @Autowired QuestionChoiceRepository choices;
    @Autowired AssignmentRepository assignments;
    @Autowired AssignmentRuleRepository rules;
    @Autowired MemberRepository members;
    @Autowired EnrollmentRepository enrollments;
    @Autowired CertificateRepository certificates;
    @MockitoBean Clock clock;
    long sourceId;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("jwt.secret", JwtTestSupport::secret);
        r.add("jwt.access-token-ttl-seconds", () -> 300);
        r.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(CLOCK.instant());
        when(clock.withZone(any())).thenAnswer(call -> CLOCK.withZone(call.getArgument(0)));
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new ClassPathResource("retraining-schema.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
        jdbc.update("INSERT INTO departments(id,code,name,is_active,created_at,updated_at) VALUES(1,'D','부서',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO job_positions(id,code,name,is_active,created_at,updated_at) VALUES(1,'J','직무',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (int id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO members(id,employee_number,email,password_hash,name,department_id,job_position_id,status,hire_date,created_at,updated_at) VALUES(?,?,?,'hash',?,1,1,?,DATE '2026-09-01',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    id, "E" + id, "m" + id + "@example.com", "직원" + id, id <= 2 ? "ACTIVE" : id == 3 ? "ON_LEAVE" : "RESIGNED");
            jdbc.update("INSERT INTO member_roles(member_id,role) VALUES(?,'EMPLOYEE')", id);
        }
        jdbc.update("INSERT INTO member_roles(member_id,role) VALUES(1,'INSTRUCTOR')");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var source = courses.saveAndFlush(Course.create("원본 2026", "원본 설명", CourseType.MANDATORY,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), new BigDecimal("80"), members.findById(1L).orElseThrow()));
            source.changeStatus(CourseStatus.CLOSED);
            sourceId = source.getId();
            var content = contents.saveAndFlush(CourseContent.create(source, "필수 콘텐츠", ContentType.LINK, "https://example.com", 300, 1, true));
            var exam = exams.saveAndFlush(Exam.create(source, "평가", new BigDecimal("70"), 3));
            var question = questions.saveAndFlush(Question.create(exam, "문제", QuestionType.SINGLE_CHOICE, new BigDecimal("100"), 1));
            var correct = choices.saveAndFlush(QuestionChoice.create(question, "정답", true, 1));
            choices.saveAndFlush(QuestionChoice.create(question, "오답", false, 2));
            var assignment = assignments.saveAndFlush(Assignment.create(source, "과제", "설명", true, LocalDate.of(2026, 9, 15), new BigDecimal("80"), 1));
            rules.saveAndFlush(AssignmentRule.create(source, AssignmentRuleType.ALL_EMPLOYEES, null, null, null, true));
            rules.saveAndFlush(AssignmentRule.create(source, AssignmentRuleType.NEW_EMPLOYEE, null, null, (short) 60, true));
            var enrollment = Enrollment.manual(members.findById(2L).orElseThrow(), source, LocalDateTime.of(2026, 9, 1, 0, 0));
            enrollment.startLearning(LocalDateTime.of(2026, 9, 2, 0, 0));
            enrollment.completeLearning(LocalDateTime.of(2026, 9, 20, 0, 0));
            enrollment = enrollments.saveAndFlush(enrollment);
            certificates.saveAndFlush(Certificate.issue(enrollment, LocalDateTime.of(2026, 9, 21, 0, 0)));
            jdbc.update("INSERT INTO content_progresses(enrollment_id,course_content_id,progress_rate,version,created_at,updated_at) VALUES(?,?,100,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", enrollment.getId(), content.getId());
            jdbc.update("INSERT INTO exam_attempts(id,exam_id,enrollment_id,attempt_number,started_at) VALUES(100,?,?,1,CURRENT_TIMESTAMP)", exam.getId(), enrollment.getId());
            jdbc.update("INSERT INTO exam_answers(id,exam_attempt_id,question_id) VALUES(100,100,?)", question.getId());
            jdbc.update("INSERT INTO exam_answer_choices(exam_answer_id,question_choice_id) VALUES(100,?)", correct.getId());
            jdbc.update("INSERT INTO assignment_submissions(assignment_id,enrollment_id,submission_type,content,submitted_at,submission_count,version,created_at,updated_at) VALUES(?,?,'TEXT','제출',CURRENT_TIMESTAMP,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", assignment.getId(), enrollment.getId());
            jdbc.update("INSERT INTO notifications(member_id,enrollment_id,type,title,message,created_at) VALUES(2,?,'COURSE_COMPLETED','수료','수료',CURRENT_TIMESTAMP)", enrollment.getId());
        });
    }

    private RetrainingPolicyRequest request(boolean open, LocalDate first, int duration) {
        return new RetrainingPolicyRequest(sourceId, "재교육", true, true, open, 12, first, duration, 7);
    }
    private long policy(boolean open) { return policies.create(request(open, TODAY, 31)).id(); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private void code(org.assertj.core.api.ThrowableAssert.ThrowingCallable task, ErrorCode code) {
        assertThatThrownBy(task).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test void draftCopiesAllConfigurationWithFreshIdsAndNoLearningHistory() {
        var history = new HashMap<String, List<Map<String, Object>>>();
        for (var table : List.of("enrollments", "content_progresses", "exam_attempts", "exam_answers", "exam_answer_choices", "assignment_submissions", "certificates", "notifications"))
            history.put(table, jdbc.queryForList("SELECT * FROM " + table));
        long policy = policy(false);
        var result = occurrences.generate(policy, 1);
        long target = result.course().id();
        assertThat(result.created()).isTrue();
        assertThat(result.course().status()).isEqualTo(CourseStatus.DRAFT);
        assertThat(result.course().title()).isEqualTo("재교육 2026-09 · 1회차");
        assertThat(result.course().description()).isEqualTo("원본 설명");
        assertThat(result.course().startDate()).isEqualTo(TODAY);
        assertThat(result.course().endDate()).isEqualTo(TODAY.plusDays(30));
        assertThat(result.course().instructorId()).isEqualTo(1);
        assertThat(result.course().retrainingPolicyId()).isEqualTo(policy);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var c = contents.findByCourseIdOrderBySortOrderAsc(target).getFirst();
            assertThat(c.getCourse().getId()).isEqualTo(target);
            assertThat(c.getId()).isNotEqualTo(contents.findByCourseIdOrderBySortOrderAsc(sourceId).getFirst().getId());
            assertThat(c.getTitle()).isEqualTo("필수 콘텐츠");
            assertThat(c.getContentUrl()).isEqualTo("https://example.com");
            var e = exams.findByCourseId(target).orElseThrow();
            assertThat(e.getId()).isNotEqualTo(exams.findByCourseId(sourceId).orElseThrow().getId());
            assertThat(e.getMaxAttempts()).isEqualTo(3);
            var q = questions.findByExamIdOrderBySortOrderAsc(e.getId()).getFirst();
            assertThat(q.getId()).isNotEqualTo(jdbc.queryForObject("SELECT id FROM questions WHERE exam_id=(SELECT id FROM exams WHERE course_id=?)", Long.class, sourceId));
            var options = choices.findByQuestionIdOrderBySortOrderAsc(q.getId());
            assertThat(options).hasSize(2);
            assertThat(options.getFirst().isCorrect()).isTrue();
            assertThat(options.getFirst().getId()).isGreaterThan(2);
            var a = assignments.findByCourseIdOrderBySortOrderAsc(target).getFirst();
            assertThat(a.getId()).isNotEqualTo(assignments.findByCourseIdOrderBySortOrderAsc(sourceId).getFirst().getId());
            assertThat(a.getDueDate()).isEqualTo(TODAY.plusDays(14));
            assertThat(a.getPassingScore()).isEqualByComparingTo("80");
            var r = rules.findByCourseIdOrderByIdAsc(target);
            assertThat(r).hasSize(2);
            assertThat(r).allSatisfy(rule -> assertThat(rule.getId()).isGreaterThan(2));
        });
        history.forEach((table, before) -> assertThat(jdbc.queryForList("SELECT * FROM " + table)).as(table).isEqualTo(before));
        assertThat(policies.get(policy).nextGenerationDate()).isEqualTo(TODAY.plusYears(1).minusDays(7));
        assertThat(occurrences.generate(policy, 1).created()).isFalse();
        assertThat(occurrences.generate(policy, 1).course().id()).isEqualTo(target);
        assertThat(count("courses")).isEqualTo(2);
    }

    @Test void autoOpenUsesExistingAssignmentAndNotificationOnlyForCurrentActiveMembers() {
        long policy = policy(true);
        var result = occurrences.generate(policy, 1);
        long target = result.course().id();
        assertThat(result.course().status()).isEqualTo(CourseStatus.OPEN);
        assertThat(jdbc.queryForList("SELECT member_id FROM enrollments WHERE course_id=? ORDER BY member_id", Long.class, target)).containsExactly(1L, 2L);
        assertThat(jdbc.queryForList("SELECT status FROM enrollments WHERE course_id=?", String.class, target)).containsOnly("ASSIGNED");
        assertThat(jdbc.queryForList("SELECT assignment_source FROM enrollments WHERE course_id=?", String.class, target)).containsOnly("AUTOMATIC");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications n JOIN enrollments e ON e.id=n.enrollment_id WHERE e.course_id=? AND n.type='ENROLLMENT_ASSIGNED'", Integer.class, target)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM enrollments e JOIN assignment_rules r ON r.id=e.assignment_rule_id WHERE e.course_id=? AND r.course_id=?", Integer.class, target, target)).isEqualTo(2);
        occurrences.generate(policy, 1);
        assertThat(count("enrollments")).isEqualTo(3);
        assertThat(count("notifications")).isEqualTo(3);
    }

    @Test void concurrentRequestsSerializeAndUniqueConstraintProtectsOccurrence() throws Exception {
        long policy = policy(false);
        var gate = new CyclicBarrier(6);
        var pool = Executors.newFixedThreadPool(6);
        try {
            var futures = new ArrayList<Future<RetrainingOccurrenceService.Result>>();
            for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> { gate.await(10, TimeUnit.SECONDS); return occurrences.generate(policy, 1); }));
            var results = new ArrayList<RetrainingOccurrenceService.Result>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().filter(RetrainingOccurrenceService.Result::created)).hasSize(1);
            assertThat(results.stream().map(r -> r.course().id()).distinct()).hasSize(1);
            assertThat(count("courses")).isEqualTo(2);
            assertThat(policies.get(policy).nextOccurrenceNumber()).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update("UPDATE courses SET retraining_policy_id=?,occurrence_number=1 WHERE id=?", policy, sourceId)).isInstanceOf(DataIntegrityViolationException.class);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void schedulerCatchesUpUsingAnchorAndDefersPriorDraft() {
        long policy = policies.create(request(false, TODAY.minusDays(2), 31)).id();
        assertThat(scheduler.runDue().created()).isEqualTo(1);
        assertThat(policies.occurrences(policy, 0, 20).getContent().getFirst().startDate()).isEqualTo(TODAY.minusDays(2));
        assertThat(policies.get(policy).nextGenerationDate()).isEqualTo(TODAY.minusDays(2).plusYears(1).minusDays(7));
        code(() -> occurrences.generateAutomatically(policy, TODAY.plusYears(1)), ErrorCode.RETRAINING_DRAFT_PENDING);
        // 관리자 명시 요청만 예정일 전 다음 DRAFT를 생성할 수 있다.
        assertThat(occurrences.generate(policy, 2).created()).isTrue();
    }

    @Test void overdueSkipIsExplicitIdempotentAndDoesNotCreateRows() {
        long policy = policies.create(request(true, TODAY.minusYears(1), 31)).id();
        code(() -> occurrences.generateAutomatically(policy, TODAY), ErrorCode.RETRAINING_OVERDUE);
        code(() -> occurrences.generate(policy, 1), ErrorCode.RETRAINING_OVERDUE);
        assertThat(occurrences.skip(policy, 1).nextOccurrenceNumber()).isEqualTo(2);
        assertThat(occurrences.skip(policy, 1).nextOccurrenceNumber()).isEqualTo(2);
        assertThat(count("courses")).isEqualTo(1);
        code(() -> occurrences.skip(policy, 2), ErrorCode.RETRAINING_NOT_OVERDUE);
        assertThat(scheduler.runDue().created()).isEqualTo(1);
        code(() -> occurrences.skip(policy, 2), ErrorCode.INVALID_RETRAINING_OCCURRENCE);
    }

    @Test void requiredDeadlineYesterdayBlocksButTodayAndOptionalDeadlineDoNot() {
        long policy = policies.create(request(false, TODAY.minusDays(15), 31)).id();
        code(() -> occurrences.generateAutomatically(policy, TODAY), ErrorCode.RETRAINING_OVERDUE);
        jdbc.update("UPDATE assignments SET is_required=FALSE WHERE course_id=?", sourceId);
        assertThat(occurrences.generateAutomatically(policy, TODAY).created()).isTrue();
        jdbc.update("UPDATE assignments SET is_required=TRUE WHERE course_id=?", sourceId);
        long boundary = policies.create(request(false, TODAY.minusDays(14), 31)).id();
        assertThat(occurrences.generateAutomatically(boundary, TODAY).created()).isTrue();
    }

    @Test void invalidOffsetsOrExamRollbackEntireGenerationAndCursor() {
        long shortPolicy = policies.create(request(false, TODAY, 5)).id();
        code(() -> occurrences.generate(shortPolicy, 1), ErrorCode.RETRAINING_SOURCE_INVALID);
        long openPolicy = policy(true);
        jdbc.update("UPDATE question_choices SET is_correct=FALSE");
        code(() -> occurrences.generate(openPolicy, 1), ErrorCode.INVALID_QUESTION_CONFIGURATION);
        assertThat(count("courses")).isEqualTo(1);
        assertThat(count("course_contents")).isEqualTo(1);
        assertThat(count("assignments")).isEqualTo(1);
        assertThat(policies.get(openPolicy).nextOccurrenceNumber()).isEqualTo(1);
        assertThat(count("notifications")).isEqualTo(1);
    }

    @Test void policyScheduleLocksAfterGenerationAndInvalidNumbersAreRejected() {
        long policy = policy(false);
        code(() -> occurrences.generate(policy, 2), ErrorCode.INVALID_RETRAINING_OCCURRENCE);
        occurrences.generate(policy, 1);
        code(() -> policies.update(policy, request(false, TODAY.plusDays(1), 31)), ErrorCode.RETRAINING_SCHEDULE_LOCKED);
        code(() -> policies.update(policy, request(false, TODAY, 32)), ErrorCode.RETRAINING_SCHEDULE_LOCKED);
        policies.status(policy, false);
        assertThat(occurrences.generate(policy, 1).created()).isFalse();
        code(() -> occurrences.generate(policy, 2), ErrorCode.RETRAINING_DISABLED);
    }

    @Test void schedulerDoesNotGenerateEarlyDisabledOrManualPoliciesAndContinuesAfterFailure() {
        long invalid = policies.create(request(false, TODAY, 5)).id();
        long valid = policy(false);
        long future = policies.create(request(false, TODAY.plusYears(1), 31)).id();
        long disabled = policy(false); policies.status(disabled, false);
        var r = request(false, TODAY, 31);
        long manual = policies.create(new RetrainingPolicyRequest(sourceId, r.baseTitle(), true, false, false, 12, TODAY, 31, 7)).id();
        assertThat(scheduler.runDue()).isEqualTo(new RetrainingSchedulerService.BatchResult(1, 1, 0));
        assertThat(policies.get(invalid).nextOccurrenceNumber()).isEqualTo(1);
        assertThat(policies.get(valid).nextOccurrenceNumber()).isEqualTo(2);
        for (long id : new long[]{future, disabled, manual}) assertThat(policies.get(id).nextOccurrenceNumber()).isEqualTo(1);
    }

    @Test void autoOpenRequiresActiveRuleAndRuleValidationRollsBack() {
        long policy = policy(true);
        jdbc.update("UPDATE assignment_rules SET is_active=FALSE");
        code(() -> occurrences.generate(policy, 1), ErrorCode.RETRAINING_ACTIVE_RULE_REQUIRED);
        assertThat(count("courses")).isEqualTo(1);
        assertThat(policies.get(policy).nextOccurrenceNumber()).isEqualTo(1);
    }

    @Test void notificationFailureRollsBackCourseEnrollmentAndCursorTogether() {
        long policy = policy(true);
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT test_assignment_notification_failure CHECK(type <> 'ENROLLMENT_ASSIGNED')");
        assertThatThrownBy(() -> occurrences.generate(policy, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("courses")).isEqualTo(1);
        assertThat(count("enrollments")).isEqualTo(1);
        assertThat(count("notifications")).isEqualTo(1);
        assertThat(policies.get(policy).nextOccurrenceNumber()).isEqualTo(1);
    }

    @Test void departmentAndJobRulesAreNewReferencesAndUseCurrentMemberState() {
        jdbc.update("UPDATE assignment_rules SET rule_type='DEPARTMENT',department_id=1,new_employee_days=NULL WHERE rule_type='ALL_EMPLOYEES'");
        jdbc.update("UPDATE assignment_rules SET rule_type='JOB_POSITION',job_position_id=1,new_employee_days=NULL WHERE rule_type='NEW_EMPLOYEE'");
        long policy = policy(true);
        // 설정 시점 이후 휴직한 회원은 새 회차 자동 배정 대상에서 제외한다.
        jdbc.update("UPDATE members SET status='ON_LEAVE' WHERE id=2");
        var result = occurrences.generate(policy, 1);
        assertThat(jdbc.queryForList("SELECT member_id FROM enrollments WHERE course_id=?", Long.class, result.course().id())).containsExactly(1L);
        assertThat(jdbc.queryForList("SELECT rule_type FROM assignment_rules WHERE course_id=? ORDER BY id", String.class, result.course().id())).containsExactly("DEPARTMENT", "JOB_POSITION");
    }
}

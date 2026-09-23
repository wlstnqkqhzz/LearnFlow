package com.be.integration;

import com.be.assignment.dto.*;
import com.be.assignment.entity.AssignmentRule;
import com.be.assignment.enums.AssignmentRuleType;
import com.be.assignment.repository.AssignmentRuleRepository;
import com.be.assignment.service.*;
import com.be.auth.dto.LoginRequest;
import com.be.auth.service.*;
import com.be.course.dto.*;
import com.be.course.entity.*;
import com.be.course.enums.*;
import com.be.course.repository.*;
import com.be.course.service.*;
import com.be.enrollment.dto.*;
import com.be.enrollment.entity.*;
import com.be.enrollment.enums.*;
import com.be.enrollment.repository.*;
import com.be.enrollment.service.*;
import com.be.exam.dto.*;
import com.be.exam.entity.*;
import com.be.exam.enums.QuestionType;
import com.be.exam.repository.*;
import com.be.exam.service.*;
import com.be.global.config.MemberSupportConfig;
import com.be.global.exception.*;
import com.be.global.security.*;
import com.be.member.dto.*;
import com.be.member.entity.Member;
import com.be.member.enums.*;
import com.be.member.repository.MemberRepository;
import com.be.member.service.MemberService;
import com.be.organization.dto.*;
import com.be.organization.entity.*;
import com.be.organization.repository.*;
import com.be.organization.service.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 관리·배정·학습·시험·수료 서비스를 연결한다. 저장소만 테스트별 메모리로 대체하며 DB/잠금 검증은 아니다.
class LearningWorkflowIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);
    private static final String PASSWORD = "workflow-test-password";
    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, Department> departmentRows = new LinkedHashMap<>();
    private final Map<Long, JobPosition> positionRows = new LinkedHashMap<>();
    private final Map<Long, Member> memberRows = new LinkedHashMap<>();
    private final Map<Long, Course> courseRows = new LinkedHashMap<>();
    private final Map<Long, CourseContent> contentRows = new LinkedHashMap<>();
    private final Map<Long, AssignmentRule> ruleRows = new LinkedHashMap<>();
    private final Map<Long, Enrollment> enrollmentRows = new LinkedHashMap<>();
    private final Map<Long, ContentProgress> progressRows = new LinkedHashMap<>();
    private final Map<Long, Exam> examRows = new LinkedHashMap<>();
    private final Map<Long, Question> questionRows = new LinkedHashMap<>();
    private final Map<Long, QuestionChoice> choiceRows = new LinkedHashMap<>();
    private final Map<Long, ExamAttempt> attemptRows = new LinkedHashMap<>();
    private final Map<Long, ExamAnswer> answerRows = new LinkedHashMap<>();
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final JobPositionRepository positions = mock(JobPositionRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseContentRepository contents = mock(CourseContentRepository.class);
    private final AssignmentRuleRepository rules = mock(AssignmentRuleRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final ContentProgressRepository progresses = mock(ContentProgressRepository.class);
    private final ExamRepository exams = mock(ExamRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final QuestionChoiceRepository choices = mock(QuestionChoiceRepository.class);
    private final ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
    private final ExamAnswerRepository answers = mock(ExamAnswerRepository.class);
    private final AutoAssignmentService auto = new AutoAssignmentService(courses, rules, members, enrollments, CLOCK, mock(EntityManager.class));
    private final DepartmentService departmentService = new DepartmentService(departments);
    private final JobPositionService positionService = new JobPositionService(positions);
    private final MemberSupportConfig support = new MemberSupportConfig();
    private final org.springframework.security.crypto.password.PasswordEncoder passwords = support.passwordEncoder();
    private final MemberService memberService = new MemberService(members, departments, positions, passwords, CLOCK, auto);
    private final CourseService courseService = new CourseService(courses, members, auto);
    private final CourseContentService contentService = new CourseContentService(courses, contents);
    private final AssignmentRuleService ruleService = new AssignmentRuleService(rules, courses, departments, positions, auto);
    private final ExamConfigurationValidator validator = new ExamConfigurationValidator();
    private final ExamService examService = new ExamService(courses, exams, questions, choices, validator, attempts);
    private final QuestionService questionService = new QuestionService(examService, questions, choices, attempts, answers, validator);
    private final EnrollmentCompletionService completion = new EnrollmentCompletionService(exams, attempts, contents, progresses);
    private final ContentProgressService progressService = new ContentProgressService(enrollments, contents, progresses, completion, CLOCK, exams);
    private final ExamAttemptService attemptService = new ExamAttemptService(enrollments, courses, exams, attempts, answers, questions,
            choices, validator, new ExamGradingService(), completion, CLOCK);

    @BeforeEach
    void repositories() {
        when(departments.saveAndFlush(any())).thenAnswer(c -> save(departmentRows, c.getArgument(0)));
        when(departments.findAllForUpdate()).thenAnswer(c -> List.copyOf(departmentRows.values()));
        when(departments.findById(anyLong())).thenAnswer(c -> Optional.ofNullable(departmentRows.get(c.getArgument(0))));
        when(positions.saveAndFlush(any())).thenAnswer(c -> save(positionRows, c.getArgument(0)));
        when(positions.findById(anyLong())).thenAnswer(c -> Optional.ofNullable(positionRows.get(c.getArgument(0))));
        when(members.saveAndFlush(any())).thenAnswer(c -> save(memberRows, c.getArgument(0)));
        when(members.findByIdForUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(memberRows.get(c.getArgument(0))));
        when(members.findWithRolesById(anyLong())).thenAnswer(c -> Optional.ofNullable(memberRows.get(c.getArgument(0))));
        when(members.findByEmail(anyString())).thenAnswer(c -> memberRows.values().stream().filter(m -> m.getEmail().equals(c.getArgument(0))).findFirst());
        when(members.findAssignmentCandidates(isNull(), isNull(), isNull(), isNull())).thenAnswer(c -> memberRows.values().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE).toList());
        when(courses.saveAndFlush(any())).thenAnswer(c -> save(courseRows, c.getArgument(0)));
        when(courses.findByIdForUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(courseRows.get(c.getArgument(0))));
        when(courses.existsById(anyLong())).thenAnswer(c -> courseRows.containsKey(c.getArgument(0)));
        when(courses.findOpenForAssignment()).thenAnswer(c -> courseRows.values().stream().filter(v -> v.getStatus() == CourseStatus.OPEN).toList());
        when(contents.saveAndFlush(any())).thenAnswer(c -> save(contentRows, c.getArgument(0)));
        when(contents.findByCourseIdOrderBySortOrderAsc(anyLong())).thenAnswer(c -> contentRows.values().stream()
                .filter(v -> v.getCourse().getId().equals(c.getArgument(0))).sorted(Comparator.comparingInt(CourseContent::getSortOrder)).toList());
        when(contents.findByIdAndCourseId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(contentRows.get(c.getArgument(0)))
                .filter(v -> v.getCourse().getId().equals(c.getArgument(1))));
        when(rules.saveAndFlush(any())).thenAnswer(c -> save(ruleRows, c.getArgument(0)));
        when(rules.findForUpdate(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(ruleRows.get(c.getArgument(1)))
                .filter(v -> v.getCourse().getId().equals(c.getArgument(0))));
        when(rules.findActiveForAssignment(anyLong())).thenAnswer(c -> ruleRows.values().stream()
                .filter(v -> v.isActive() && v.getCourse().getId().equals(c.getArgument(0))).toList());
        when(enrollments.saveAndFlush(any())).thenAnswer(c -> save(enrollmentRows, c.getArgument(0)));
        when(enrollments.findExistingForAssignment(anyLong(), anyLong())).thenAnswer(c -> enrollmentRows.values().stream()
                .filter(v -> v.getMember().getId().equals(c.getArgument(0)) && v.getCourse().getId().equals(c.getArgument(1))).findFirst());
        when(enrollments.findById(anyLong())).thenAnswer(c -> Optional.ofNullable(enrollmentRows.get(c.getArgument(0))));
        when(enrollments.findForExamUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(enrollmentRows.get(c.getArgument(0))));
        when(enrollments.findForProgressUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(enrollmentRows.get(c.getArgument(0))));
        when(enrollments.findCourseId(anyLong())).thenAnswer(c -> Optional.ofNullable(enrollmentRows.get(c.getArgument(0))).map(v -> v.getCourse().getId()));
        when(enrollments.findOverdueIds(anyList(), any(), anyLong(), any())).thenAnswer(c -> enrollmentRows.values().stream()
                .filter(v -> ((List<?>) c.getArgument(0)).contains(v.getStatus()) && v.getDueDate().isBefore(c.getArgument(1))
                        && v.getId() > (Long) c.getArgument(2)).map(Enrollment::getId).sorted().toList());
        when(progresses.saveAndFlush(any())).thenAnswer(c -> save(progressRows, c.getArgument(0)));
        when(progresses.findByEnrollmentId(anyLong())).thenAnswer(c -> progressRows.values().stream()
                .filter(v -> v.getEnrollment().getId().equals(c.getArgument(0))).toList());
        when(progresses.findByEnrollmentIdAndCourseContentId(anyLong(), anyLong())).thenAnswer(c -> progressRows.values().stream()
                .filter(v -> v.getEnrollment().getId().equals(c.getArgument(0)) && v.getCourseContent().getId().equals(c.getArgument(1))).findFirst());
        when(exams.saveAndFlush(any())).thenAnswer(c -> save(examRows, c.getArgument(0)));
        when(exams.existsByCourseId(anyLong())).thenAnswer(c -> examRows.values().stream().anyMatch(v -> v.getCourse().getId().equals(c.getArgument(0))));
        when(exams.findByCourseId(anyLong())).thenAnswer(c -> examRows.values().stream().filter(v -> v.getCourse().getId().equals(c.getArgument(0))).findFirst());
        when(questions.saveAndFlush(any())).thenAnswer(c -> save(questionRows, c.getArgument(0)));
        when(questions.findByExamIdOrderBySortOrderAsc(anyLong())).thenAnswer(c -> questionRows.values().stream()
                .filter(v -> v.getExam().getId().equals(c.getArgument(0))).sorted(Comparator.comparingInt(Question::getSortOrder)).toList());
        when(questions.findByIdAndExamId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(questionRows.get(c.getArgument(0)))
                .filter(v -> v.getExam().getId().equals(c.getArgument(1))));
        when(choices.saveAllAndFlush(any())).thenAnswer(c -> {
            List<QuestionChoice> items = c.getArgument(0);
            items.forEach(v -> save(choiceRows, v));
            return items;
        });
        when(choices.findByQuestionIdOrderBySortOrderAsc(anyLong())).thenAnswer(c -> choiceRows.values().stream()
                .filter(v -> v.getQuestion().getId().equals(c.getArgument(0))).sorted(Comparator.comparingInt(QuestionChoice::getSortOrder)).toList());
        when(choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(anyLong())).thenAnswer(c -> choiceRows.values().stream()
                .filter(v -> v.getQuestion().getExam().getId().equals(c.getArgument(0))).toList());
        when(attempts.saveAndFlush(any())).thenAnswer(c -> save(attemptRows, c.getArgument(0)));
        when(attempts.findByEnrollmentIdOrderByAttemptNumberAsc(anyLong())).thenAnswer(c -> attemptRows.values().stream()
                .filter(v -> v.getEnrollment().getId().equals(c.getArgument(0))).sorted(Comparator.comparingInt(ExamAttempt::getAttemptNumber)).toList());
        when(attempts.existsByExamId(anyLong())).thenAnswer(c -> attemptRows.values().stream().anyMatch(v -> v.getExam().getId().equals(c.getArgument(0))));
        when(attempts.existsByEnrollmentIdAndSubmittedAtIsNotNullAndPassedTrue(anyLong())).thenAnswer(c -> attemptRows.values().stream()
                .anyMatch(v -> v.getEnrollment().getId().equals(c.getArgument(0)) && v.getSubmittedAt() != null && Boolean.TRUE.equals(v.getPassed())));
        when(attempts.findById(anyLong())).thenAnswer(c -> Optional.ofNullable(attemptRows.get(c.getArgument(0))));
        when(attempts.findForUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(attemptRows.get(c.getArgument(0))));
        when(attempts.findEnrollmentId(anyLong())).thenAnswer(c -> Optional.ofNullable(attemptRows.get(c.getArgument(0))).map(v -> v.getEnrollment().getId()));
        when(answers.saveAndFlush(any())).thenAnswer(c -> save(answerRows, c.getArgument(0)));
        when(answers.findByExamAttemptId(anyLong())).thenAnswer(c -> answerRows.values().stream()
                .filter(v -> v.getExamAttempt().getId().equals(c.getArgument(0))).toList());
        when(answers.findByExamAttemptIdAndQuestionId(anyLong(), anyLong())).thenAnswer(c -> answerRows.values().stream()
                .filter(v -> v.getExamAttempt().getId().equals(c.getArgument(0)) && v.getQuestion().getId().equals(c.getArgument(1))).findFirst());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void fullWorkflowCompletesInEitherLearningOrder(boolean contentFirst) {
        var scenario = prepare(true, true, true);
        assertThat(scenario.enrollment().getStatus()).isEqualTo(EnrollmentStatus.ASSIGNED);
        if (contentFirst) learn(scenario, "100");
        long attemptId = attemptService.start(scenario.enrollment().getId(), scenario.principal()).attempt().attemptId();
        var startedAt = scenario.enrollment().getStartedAt();
        assertThat(startedAt).isEqualTo(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        assertThat(scenario.enrollment().getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        assertThat(scenario.enrollment().getCompletedAt()).isNull();
        answerCorrectly(attemptId, scenario);
        var result = attemptService.submit(attemptId, scenario.principal());
        assertThat(result.score()).isEqualByComparingTo("100.00");
        assertThat(result.passed()).isTrue();
        if (!contentFirst) {
            assertThat(scenario.enrollment().getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
            learn(scenario, "100");
        }
        assertThat(scenario.enrollment().getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(scenario.enrollment().getStartedAt()).isEqualTo(startedAt);
        assertThat(scenario.enrollment().getCompletedAt()).isNotNull();
        assertThat(answerRows).hasSize(3);
        assertThat(answerRows.values()).allSatisfy(a -> assertThat(a.getEarnedScore()).isEqualByComparingTo(a.getQuestion().getScore()));
        int count = enrollmentRows.size();
        auto.assignCourse(scenario.courseId());
        assertThat(enrollmentRows).hasSize(count);
        assertThat(scenario.enrollment().getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    }

    @Test
    void noExamAndNoInstructorStillOpensAssignsAndCompletes() {
        var scenario = prepare(false, true, false);
        assertThat(courseRows.get(scenario.courseId()).getInstructor()).isNull();
        assertThat(courseRows.get(scenario.courseId()).getStatus()).isEqualTo(CourseStatus.OPEN);
        assertThat(learn(scenario, "100").status()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(attemptRows).isEmpty();
    }

    @Test
    void examOnlyCourseCompletesWithoutRequiredContents() {
        var scenario = prepare(true, false, false);
        long attemptId = attemptService.start(scenario.enrollment().getId(), scenario.principal()).attempt().attemptId();
        answerCorrectly(attemptId, scenario);
        assertThat(attemptService.submit(attemptId, scenario.principal()).passed()).isTrue();
        assertThat(scenario.enrollment().getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(progressRows).isEmpty();
    }

    @Test
    void threeFailuresBlockFurtherExamsAndProgressWithoutErasingHistory() {
        var scenario = prepare(true, true, false);
        for (int number = 1; number <= 3; number++) {
            var attempt = attemptService.start(scenario.enrollment().getId(), scenario.principal()).attempt();
            assertThat(attempt.attemptNumber()).isEqualTo(number);
            assertThat(attemptService.submit(attempt.attemptId(), scenario.principal()).passed()).isFalse();
            assertThat(scenario.enrollment().getStatus()).isEqualTo(number == 3 ? EnrollmentStatus.FAILED : EnrollmentStatus.IN_PROGRESS);
        }
        assertThat(attemptRows).hasSize(3);
        assertThat(scenario.enrollment().getStartedAt()).isNotNull();
        assertThat(scenario.enrollment().getCompletedAt()).isNull();
        error(() -> attemptService.start(scenario.enrollment().getId(), scenario.principal()), ErrorCode.ENROLLMENT_EXAM_NOT_EDITABLE);
        error(() -> learn(scenario, "100"), ErrorCode.ENROLLMENT_PROGRESS_NOT_EDITABLE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void expirationBlocksActivitiesAndAutomaticTriggersPreserveTheSameEnrollment(boolean started) {
        var scenario = prepare(true, true, false);
        if (started) learn(scenario, "20");
        var startedAt = scenario.enrollment().getStartedAt();
        var source = scenario.enrollment().getAssignmentSource();
        var rule = scenario.enrollment().getAssignmentRule();
        var expiration = new EnrollmentExpirationService(enrollments, new EnrollmentExpirationProcessor(enrollments),
                Clock.offset(CLOCK, Duration.ofDays(40)));
        assertThat(expiration.expireOverdueEnrollments().expiredCount()).isEqualTo(1);
        assertThat(expiration.expireOverdueEnrollments().expiredCount()).isZero();
        auto.assignMember(memberRows.get(scenario.principal().memberId()));
        auto.assignCourse(scenario.courseId());
        ruleService.changeStatus(scenario.courseId(), rule.getId(), new AssignmentRuleStatusRequest(true));
        assertThat(enrollmentRows).hasSize(1);
        assertThat(enrollmentRows.get(scenario.enrollment().getId())).isSameAs(scenario.enrollment());
        assertThat(scenario.enrollment().getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
        assertThat(scenario.enrollment().getStartedAt()).isEqualTo(startedAt);
        assertThat(scenario.enrollment().getCompletedAt()).isNull();
        assertThat(scenario.enrollment().getAssignmentSource()).isEqualTo(source);
        assertThat(scenario.enrollment().getAssignmentRule()).isSameAs(rule);
        error(() -> learn(scenario, "100"), ErrorCode.ENROLLMENT_PROGRESS_NOT_EDITABLE);
        error(() -> attemptService.start(scenario.enrollment().getId(), scenario.principal()), ErrorCode.ENROLLMENT_EXAM_NOT_EDITABLE);
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"ON_LEAVE", "RESIGNED"})
    void employmentStatusChangesPreserveHistoryAndControlLoginAndNewAssignments(MemberStatus status) {
        var scenario = prepare(true, true, false);
        learn(scenario, "20");
        var member = memberRows.get(scenario.principal().memberId());
        memberService.changeStatus(member.getId(), new MemberStatusUpdateRequest(status));
        long nextCourse = courseService.create(new CourseCreateRequest("다음 교육", null, CourseType.MANDATORY, TODAY,
                TODAY.plusDays(30), null, null)).id();
        ruleService.create(nextCourse, new AssignmentRuleCreateRequest(AssignmentRuleType.ALL_EMPLOYEES, null, null, null, true));
        courseService.changeStatus(nextCourse, new CourseStatusRequest(CourseStatus.OPEN));
        auto.assignMember(member);
        assertThat(enrollmentRows).hasSize(1);
        assertThat(progressRows).hasSize(1);
        assertThat(scenario.enrollment().getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        var tokens = new JwtTokenProvider(new JwtProperties(Base64.getEncoder().encodeToString(key), 600, 3600), CLOCK);
        var refresh = mock(RefreshTokenService.class);
        var auth = new AuthService(members, passwords, tokens, refresh, new MemberAuthenticationService(members));
        if (status == MemberStatus.ON_LEAVE) {
            assertThat(tokens.parseAccessToken(auth.login(new LoginRequest(member.getEmail(), PASSWORD)).accessToken()).memberId()).isEqualTo(member.getId());
        } else {
            assertThatThrownBy(() -> auth.login(new LoginRequest(member.getEmail(), PASSWORD))).isInstanceOf(BadCredentialsException.class);
            verifyNoInteractions(refresh);
        }
    }

    @Test
    void foreignOwnerCannotReadOrChangeRealProgressAndAttemptServices() {
        var scenario = prepare(true, true, true);
        var other = MemberPrincipal.from(memberRows.values().stream().filter(m -> !m.getId().equals(scenario.principal().memberId())).findFirst().orElseThrow());
        long enrollmentId = scenario.enrollment().getId();
        error(() -> progressService.get(enrollmentId, other), ErrorCode.ENROLLMENT_PROGRESS_ACCESS_DENIED);
        error(() -> progressService.update(enrollmentId, scenario.contentId(), other, new ContentProgressUpdateRequest(BigDecimal.TEN)), ErrorCode.ENROLLMENT_PROGRESS_ACCESS_DENIED);
        error(() -> attemptService.start(enrollmentId, other), ErrorCode.EXAM_ACCESS_DENIED);
        long attemptId = attemptService.start(enrollmentId, scenario.principal()).attempt().attemptId();
        var question = questionRows.values().iterator().next();
        error(() -> attemptService.paper(attemptId, other), ErrorCode.EXAM_ACCESS_DENIED);
        error(() -> attemptService.saveAnswer(attemptId, question.getId(), other, new AnswerSaveRequest(List.of())), ErrorCode.EXAM_ACCESS_DENIED);
        error(() -> attemptService.submit(attemptId, other), ErrorCode.EXAM_ACCESS_DENIED);
        error(() -> attemptService.result(attemptId, other), ErrorCode.EXAM_ACCESS_DENIED);
        error(() -> attemptService.history(enrollmentId, other), ErrorCode.EXAM_ACCESS_DENIED);
        assertThat(answerRows).isEmpty();
        assertThat(progressRows).isEmpty();
    }

    @Test
    void allStudentResponsesOmitInternalAnswerKeysAndUnsubmittedGrades() {
        var scenario = prepare(true, true, false);
        var mapper = new tools.jackson.databind.ObjectMapper();
        var start = attemptService.start(scenario.enrollment().getId(), scenario.principal());
        long attemptId = start.attempt().attemptId();
        assertThat(start.attempt().score()).isNull();
        assertThat(start.attempt().passed()).isNull();
        var question = questionRows.values().iterator().next();
        long choiceId = choiceRows.values().stream().filter(c -> c.getQuestion() == question).findFirst().orElseThrow().getId();
        var answer = attemptService.saveAnswer(attemptId, question.getId(), scenario.principal(), new AnswerSaveRequest(List.of(choiceId)));
        var before = attemptService.history(scenario.enrollment().getId(), scenario.principal());
        assertThat(before.getFirst().score()).isNull();
        var paper = attemptService.paper(attemptId, scenario.principal());
        var submitted = attemptService.submit(attemptId, scenario.principal());
        for (var response : List.of(start.attempt(), answer, before, paper, submitted,
                attemptService.result(attemptId, scenario.principal()),
                attemptService.history(scenario.enrollment().getId(), scenario.principal()))) {
            assertThat(mapper.writeValueAsString(response)).doesNotContain("\"correct\"", "\"isCorrect\"",
                    "correctChoiceIds", "correctCount", "earnedScore");
        }
    }

    private Scenario prepare(boolean withExam, boolean withContent, boolean withInstructor) {
        long departmentId = departmentService.create(new DepartmentCreateRequest("DEV", "개발", null)).id();
        long positionId = positionService.create(new JobPositionCreateRequest("DEV", "개발자")).id();
        Long instructorId = null;
        if (withInstructor) {
            instructorId = memberService.create(new MemberCreateRequest("I", "instructor@example.com", PASSWORD, "강사", departmentId, positionId, TODAY)).id();
            memberService.addRole(instructorId, new MemberRoleUpdateRequest(Role.INSTRUCTOR));
        }
        long employeeId = memberService.create(new MemberCreateRequest("E", "employee@example.com", PASSWORD, "직원", departmentId, positionId, TODAY)).id();
        long courseId = courseService.create(new CourseCreateRequest("통합 교육", null, CourseType.MANDATORY, TODAY,
                TODAY.plusDays(30), new BigDecimal("100"), instructorId)).id();
        assertThat(courseRows.get(courseId).getStatus()).isEqualTo(CourseStatus.DRAFT);
        Long contentId = withContent ? contentService.create(courseId,
                new CourseContentCreateRequest("필수 영상", ContentType.VIDEO, "https://example.com/video", 60, 1, true)).id() : null;
        if (withExam) {
            examService.create(courseId, new ExamCreateRequest("종합 시험", new BigDecimal("70"), 3));
            int order = 0;
            for (QuestionType type : QuestionType.values()) {
                var options = type == QuestionType.TRUE_FALSE
                        ? List.of(new ChoiceCreateRequest("TRUE", true, 1), new ChoiceCreateRequest("FALSE", false, 2))
                        : List.of(new ChoiceCreateRequest("A", true, 1), new ChoiceCreateRequest("B", type == QuestionType.MULTIPLE_CHOICE, 2));
                questionService.create(courseId, new QuestionCreateRequest("문제 " + type, type, BigDecimal.TEN, ++order, options));
            }
        }
        ruleService.create(courseId, new AssignmentRuleCreateRequest(AssignmentRuleType.ALL_EMPLOYEES, null, null, null, true));
        assertThat(enrollmentRows).isEmpty();
        courseService.changeStatus(courseId, new CourseStatusRequest(CourseStatus.OPEN));
        var enrollment = enrollmentRows.values().stream().filter(e -> e.getMember().getId().equals(employeeId)).findFirst().orElseThrow();
        assertThat(enrollment.getAssignmentSource()).isEqualTo(AssignmentSource.AUTOMATIC);
        return new Scenario(courseId, contentId, enrollment, MemberPrincipal.from(memberRows.get(employeeId)));
    }

    private EnrollmentProgressResponse learn(Scenario scenario, String rate) {
        return progressService.update(scenario.enrollment().getId(), scenario.contentId(), scenario.principal(), new ContentProgressUpdateRequest(new BigDecimal(rate)));
    }

    private void answerCorrectly(long attemptId, Scenario scenario) {
        for (var question : questionRows.values()) {
            var selected = choiceRows.values().stream().filter(c -> c.getQuestion() == question && c.isCorrect()).map(QuestionChoice::getId).toList();
            attemptService.saveAnswer(attemptId, question.getId(), scenario.principal(), new AnswerSaveRequest(selected));
        }
    }

    private <T> T save(Map<Long, T> rows, T entity) {
        Long id = (Long) ReflectionTestUtils.getField(entity, "id");
        if (id == null) { id = sequence.incrementAndGet(); ReflectionTestUtils.setField(entity, "id", id); }
        rows.put(id, entity);
        return entity;
    }

    private void error(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private record Scenario(long courseId, Long contentId, Enrollment enrollment, MemberPrincipal principal) {}
}

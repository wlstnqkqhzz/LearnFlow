package com.be.exam;

import com.be.assignment.AssignmentFixtures;
import com.be.course.entity.CourseContent;
import com.be.course.enums.ContentType;
import com.be.course.repository.*;
import com.be.enrollment.entity.*;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.*;
import com.be.enrollment.service.*;
import com.be.exam.dto.*;
import com.be.exam.entity.*;
import com.be.exam.enums.QuestionType;
import com.be.exam.repository.*;
import com.be.exam.service.*;
import com.be.global.exception.*;
import com.be.global.security.MemberPrincipal;
import com.be.member.enums.Role;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;

import static com.be.exam.ExamFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 저장소만 대체하고 실제 채점·수료 Service와 Entity를 연결한 워크플로 테스트
class ExamAttemptServiceTest {
    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final CourseRepository courses = mock(CourseRepository.class);
    final ExamRepository exams = mock(ExamRepository.class);
    final ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
    final ExamAnswerRepository answers = mock(ExamAnswerRepository.class);
    final QuestionRepository questions = mock(QuestionRepository.class);
    final QuestionChoiceRepository choices = mock(QuestionChoiceRepository.class);
    final CourseContentRepository contents = mock(CourseContentRepository.class);
    final ContentProgressRepository progresses = mock(ContentProgressRepository.class);
    final EnrollmentCompletionService completion = new EnrollmentCompletionService(exams, attempts, contents, progresses);
    final ExamAttemptService service = new ExamAttemptService(enrollments, courses, exams, attempts, answers, questions,
            choices, new ExamConfigurationValidator(), new ExamGradingService(), completion, AssignmentFixtures.CLOCK);
    final MemberPrincipal owner = new MemberPrincipal(2L, "owner@example.com", Set.of(Role.EMPLOYEE));
    final MemberPrincipal other = new MemberPrincipal(3L, "other@example.com", Set.of(Role.EMPLOYEE));
    final MemberPrincipal admin = new MemberPrincipal(3L, "admin@example.com", Set.of(Role.ADMIN, Role.EMPLOYEE));
    final List<ExamAttempt> history = new ArrayList<>();
    final List<ExamAnswer> saved = new ArrayList<>();
    final LocalDateTime now = LocalDateTime.now(AssignmentFixtures.CLOCK);
    Exam exam;
    Enrollment enrollment;
    Question question;
    List<QuestionChoice> options;

    @BeforeEach void setup() {
        exam = exam(); question = question(exam, 1, QuestionType.SINGLE_CHOICE); options = choices(question);
        enrollment = Enrollment.manual(AssignmentFixtures.member(2L), exam.getCourse(), now.minusDays(1));
        AssignmentFixtures.id(enrollment, 10L);
        when(enrollments.findCourseId(10L)).thenReturn(Optional.of(1L));
        when(enrollments.findForExamUpdate(10L)).thenReturn(Optional.of(enrollment));
        when(enrollments.findForProgressUpdate(10L)).thenReturn(Optional.of(enrollment));
        when(enrollments.findById(10L)).thenReturn(Optional.of(enrollment));
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(exam.getCourse()));
        when(exams.findByCourseId(1L)).thenReturn(Optional.of(exam));
        when(exams.existsByCourseId(1L)).thenReturn(true);
        when(questions.findByExamIdOrderBySortOrderAsc(10L)).thenReturn(List.of(question));
        when(questions.findByIdAndExamId(1L, 10L)).thenReturn(Optional.of(question));
        when(choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(10L)).thenReturn(options);
        when(choices.findByQuestionIdOrderBySortOrderAsc(1L)).thenReturn(options);
        when(attempts.findByEnrollmentIdOrderByAttemptNumberAsc(10L)).thenAnswer(call -> new ArrayList<>(history));
        when(attempts.existsByEnrollmentIdAndSubmittedAtIsNotNullAndPassedTrue(10L)).thenAnswer(call ->
                history.stream().anyMatch(a -> a.getSubmittedAt() != null && Boolean.TRUE.equals(a.getPassed())));
        doAnswer(call -> { ExamAttempt a = call.getArgument(0); AssignmentFixtures.id(a, 30L + history.size()); history.add(a); return a; })
                .when(attempts).saveAndFlush(any());
        when(attempts.findById(anyLong())).thenAnswer(call -> history.stream().filter(a -> a.getId().equals(call.getArgument(0))).findFirst());
        when(attempts.findForUpdate(anyLong())).thenAnswer(call -> history.stream().filter(a -> a.getId().equals(call.getArgument(0))).findFirst());
        when(attempts.findEnrollmentId(anyLong())).thenAnswer(call -> history.stream().filter(a -> a.getId().equals(call.getArgument(0)))
                .map(a -> a.getEnrollment().getId()).findFirst());
        when(answers.findByExamAttemptId(anyLong())).thenAnswer(call -> saved.stream().filter(a -> a.getExamAttempt().getId().equals(call.getArgument(0))).toList());
        when(answers.findByExamAttemptIdAndQuestionId(anyLong(), anyLong())).thenAnswer(call -> saved.stream()
                .filter(a -> a.getExamAttempt().getId().equals(call.getArgument(0)) && a.getQuestion().getId().equals(call.getArgument(1))).findFirst());
        doAnswer(call -> { ExamAnswer a = call.getArgument(0); if (!saved.contains(a)) saved.add(a); return a; }).when(answers).saveAndFlush(any());
    }

    @Test void startsAssignedAndReusesOpenAttemptWithoutConsumingQuota() {
        var first = service.start(10L, owner);
        assertThat(first.created()).isTrue(); assertThat(first.attempt().attemptNumber()).isEqualTo(1);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        assertThat(enrollment.getStartedAt()).isEqualTo(now);
        assertThat(history.getFirst().getScore()).isNull(); assertThat(history.getFirst().getPassed()).isNull();
        var again = service.start(10L, owner);
        assertThat(again.created()).isFalse(); assertThat(again.attempt().attemptId()).isEqualTo(first.attempt().attemptId());
        assertThat(history).hasSize(1);
        var order = inOrder(courses, enrollments);
        order.verify(courses).findByIdForUpdate(1L); order.verify(enrollments).findForExamUpdate(10L);
    }

    @Test void startPreservesEarlierLearningTime() {
        enrollment.startLearning(now.minusHours(1)); service.start(10L, owner);
        assertThat(enrollment.getStartedAt()).isEqualTo(now.minusHours(1));
    }

    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = {"COMPLETED", "FAILED", "EXPIRED"})
    void terminalCannotStartOrSaveOrSubmit(EnrollmentStatus status) {
        var id = service.start(10L, owner).attempt().attemptId();
        ReflectionTestUtils.setField(enrollment, "status", status);
        error(() -> service.start(10L, owner), ErrorCode.ENROLLMENT_EXAM_NOT_EDITABLE);
        error(() -> service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(100L))), ErrorCode.ENROLLMENT_EXAM_NOT_EDITABLE);
        error(() -> service.submit(id, owner), ErrorCode.ENROLLMENT_EXAM_NOT_EDITABLE);
    }

    @Test void missingExamAndInvalidConfigurationPreventCreation() {
        when(exams.findByCourseId(1L)).thenReturn(Optional.empty());
        error(() -> service.start(10L, owner), ErrorCode.EXAM_NOT_FOUND);
        when(exams.findByCourseId(1L)).thenReturn(Optional.of(exam));
        when(questions.findByExamIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        error(() -> service.start(10L, owner), ErrorCode.INVALID_EXAM_CONFIGURATION);
        assertThat(history).isEmpty();
    }

    @Test void foreignEnrollmentAndAttemptAccessDeniedIncludingAdminWrites() {
        error(() -> service.start(10L, other), ErrorCode.EXAM_ACCESS_DENIED);
        long id = service.start(10L, owner).attempt().attemptId();
        for (var principal : List.of(other, admin)) {
            error(() -> service.paper(id, principal), ErrorCode.EXAM_ACCESS_DENIED);
            error(() -> service.saveAnswer(id, 1L, principal, new AnswerSaveRequest(List.of(100L))), ErrorCode.EXAM_ACCESS_DENIED);
            error(() -> service.submit(id, principal), ErrorCode.EXAM_ACCESS_DENIED);
        }
        error(() -> service.history(10L, other), ErrorCode.EXAM_ACCESS_DENIED);
        assertThat(service.history(10L, admin)).hasSize(1);
    }

    @Test void saveReplacesSelectionsAndLeavesGradeNull() {
        long id = service.start(10L, owner).attempt().attemptId();
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(100L)));
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(101L)));
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getSelectedChoices()).extracting(QuestionChoice::getId).containsExactly(101L);
        assertThat(saved.getFirst().getEarnedScore()).isNull(); assertThat(saved.getFirst().getIsCorrect()).isNull();
    }

    @ParameterizedTest @EnumSource(value = QuestionType.class, names = {"SINGLE_CHOICE", "TRUE_FALSE"})
    void singleAndTrueFalseRequireOneChoice(QuestionType type) {
        question.update("문제", type, BigDecimal.TEN, 1);
        long id = service.start(10L, owner).attempt().attemptId();
        for (var ids : List.of(List.<Long>of(), List.of(100L, 101L), List.of(100L, 100L), List.of(999L)))
            error(() -> service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(ids)), ErrorCode.INVALID_EXAM_ANSWER);
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(100L)));
    }

    @Test void multipleAllowsEmptyAndManyButNotDuplicatesOrForeignChoice() {
        question.update("문제", QuestionType.MULTIPLE_CHOICE, BigDecimal.TEN, 1);
        long id = service.start(10L, owner).attempt().attemptId();
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(101L, 100L)));
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of()));
        assertThat(saved.getFirst().getSelectedChoices()).isEmpty();
        error(() -> service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(100L, 100L))), ErrorCode.INVALID_EXAM_ANSWER);
        error(() -> service.saveAnswer(id, 999L, owner, new AnswerSaveRequest(List.of(100L))), ErrorCode.QUESTION_NOT_FOUND);
    }

    @Test void passExamOnlyCompletesAndResultCanBeReadByAdmin() {
        long id = service.start(10L, owner).attempt().attemptId();
        error(() -> service.result(id, owner), ErrorCode.EXAM_ATTEMPT_NOT_SUBMITTED);
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(100L)));
        var result = service.submit(id, owner);
        assertThat(result.score()).isEqualByComparingTo("100.00"); assertThat(result.passed()).isTrue();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getCompletedAt()).isEqualTo(now);
        assertThat(saved.getFirst().getEarnedScore()).isEqualByComparingTo("10");
        assertThat(service.result(id, admin).score()).isEqualByComparingTo("100");
        error(() -> service.submit(id, owner), ErrorCode.EXAM_ATTEMPT_ALREADY_SUBMITTED);
        error(() -> service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(101L))), ErrorCode.EXAM_ATTEMPT_ALREADY_SUBMITTED);
    }

    @Test void examFirstThenProgressUsesSameCompletionEvaluator() {
        var content = requiredContent();
        long id = service.start(10L, owner).attempt().attemptId();
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(100L))); service.submit(id, owner);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        error(() -> service.start(10L, owner), ErrorCode.EXAM_ALREADY_PASSED);
        var progressService = new ContentProgressService(enrollments, contents, progresses, completion, AssignmentFixtures.CLOCK);
        doAnswer(call -> { ContentProgress p = call.getArgument(0); doReturn(List.of(p)).when(progresses).findByEnrollmentId(10L); return p; })
                .when(progresses).saveAndFlush(any());
        progressService.update(10L, content.getId(), owner, new com.be.enrollment.dto.ContentProgressUpdateRequest(new BigDecimal("100")));
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    }

    @Test void contentFirstThenExamCompletes() {
        var content = requiredContent();
        var progress = ContentProgress.create(enrollment, content); progress.updateProgress(new BigDecimal("100"), now);
        when(progresses.findByEnrollmentId(10L)).thenReturn(List.of(progress));
        long id = service.start(10L, owner).attempt().attemptId();
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(100L))); service.submit(id, owner);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    }

    @Test void unansweredFailuresConsumeQuotaAndFinalFailureIsTerminal() {
        for (int number = 1; number <= 3; number++) {
            var started = service.start(10L, owner).attempt(); assertThat(started.attemptNumber()).isEqualTo(number);
            var result = service.submit(started.attemptId(), owner);
            assertThat(result.score()).isEqualByComparingTo("0"); assertThat(result.passed()).isFalse();
            assertThat(result.remainingAttempts()).isEqualTo(3 - number);
            assertThat(enrollment.getStatus()).isEqualTo(number == 3 ? EnrollmentStatus.FAILED : EnrollmentStatus.IN_PROGRESS);
        }
        assertThat(enrollment.getCompletedAt()).isNull(); assertThat(enrollment.getStartedAt()).isEqualTo(now);
        assertThat(saved).isEmpty(); error(() -> service.start(10L, owner), ErrorCode.ENROLLMENT_EXAM_NOT_EDITABLE);
    }

    @Test void quotaLimitCheckedEvenIfLegacyEnrollmentStillInProgress() {
        exam.update("시험", BigDecimal.TEN, 1);
        var id = service.start(10L, owner).attempt().attemptId(); history.getFirst().submit(BigDecimal.ZERO, false, now);
        error(() -> service.start(10L, owner), ErrorCode.EXAM_ATTEMPTS_EXHAUSTED);
    }

    @Test void missingResourcesAndInvalidSubmitDoNotGrade() {
        error(() -> service.paper(999L, owner), ErrorCode.EXAM_ATTEMPT_NOT_FOUND);
        error(() -> service.start(999L, owner), ErrorCode.ENROLLMENT_NOT_FOUND);
        long id = service.start(10L, owner).attempt().attemptId();
        when(questions.findByExamIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        error(() -> service.submit(id, owner), ErrorCode.INVALID_EXAM_CONFIGURATION);
        assertThat(history.getFirst().getSubmittedAt()).isNull();
    }

    @ParameterizedTest @CsvSource({"79.99,false", "80.00,true"})
    void normalizedPassingBoundary(String raw, boolean passed) {
        question.update("Q1", question.getQuestionType(), new BigDecimal(raw), 1);
        var q2 = question(exam, 2, QuestionType.SINGLE_CHOICE);
        q2.update("Q2", q2.getQuestionType(), new BigDecimal("100").subtract(new BigDecimal(raw)), 2);
        when(questions.findByExamIdOrderBySortOrderAsc(10L)).thenReturn(List.of(question, q2));
        var all = new ArrayList<>(options); all.add(choice(q2, 102, "A", true, 1));
        when(choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(10L)).thenReturn(all);
        long id = service.start(10L, owner).attempt().attemptId();
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(100L)));
        assertThat(service.submit(id, owner).passed()).isEqualTo(passed);
    }

    @Test void completionFailureIsPropagatedSoOuterTransactionCannotCommit() {
        var failing = mock(EnrollmentCompletionService.class);
        var tested = new ExamAttemptService(enrollments, courses, exams, attempts, answers, questions, choices,
                new ExamConfigurationValidator(), new ExamGradingService(), failing, AssignmentFixtures.CLOCK);
        long id = service.start(10L, owner).attempt().attemptId();
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(100L)));
        doThrow(new IllegalStateException("completion failed")).when(failing).evaluate(eq(enrollment), any(LocalDateTime.class));
        assertThatThrownBy(() -> tested.submit(id, owner)).isInstanceOf(IllegalStateException.class);
        // 실제 DB 롤백은 통합 환경에서 별도 검증; 여기서는 예외를 삼키지 않는지만 확인
    }

    @Test void actualPaperUsesStudentDtoAndPreservesOrderAndSavedChoices() {
        long id = service.start(10L, owner).attempt().attemptId();
        service.saveAnswer(id, 1L, owner, new AnswerSaveRequest(List.of(101L)));
        var paper = service.paper(id, owner);
        assertThat(paper.questions()).extracting(AttemptPaperResponse.QuestionItem::questionId).containsExactly(1L);
        assertThat(paper.questions().getFirst().choices()).extracting(AttemptPaperResponse.ChoiceItem::choiceId).containsExactly(100L, 101L);
        assertThat(paper.questions().getFirst().selectedChoiceIds()).containsExactly(101L);
        var json = new tools.jackson.databind.ObjectMapper().writeValueAsString(paper);
        assertThat(json).doesNotContain("\"correct\"", "correctChoiceIds", "earnedScore", "isCorrect");
    }

    @Test void lockConflictPropagatesWithoutCreatingAttemptOrSwallowingFailure() {
        var conflict = new org.springframework.dao.CannotAcquireLockException("lock conflict");
        when(enrollments.findForExamUpdate(10L)).thenThrow(conflict);
        assertThatThrownBy(() -> service.start(10L, owner)).isSameAs(conflict);
        assertThat(history).isEmpty();
    }

    private CourseContent requiredContent() {
        var content = CourseContent.create(exam.getCourse(), "콘텐츠", ContentType.VIDEO, "https://example.com", null, 1, true);
        AssignmentFixtures.id(content, 20L);
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(content));
        when(contents.findByIdAndCourseId(20L, 1L)).thenReturn(Optional.of(content));
        return content;
    }
    private void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
}

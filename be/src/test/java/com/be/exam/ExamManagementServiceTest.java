package com.be.exam;

import com.be.assignment.AssignmentFixtures;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.exam.dto.*;
import com.be.exam.entity.*;
import com.be.exam.enums.QuestionType;
import com.be.exam.repository.*;
import com.be.exam.service.*;
import com.be.global.exception.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import static com.be.exam.ExamFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 실제 Service/Validator/Entity 조합으로 업무 규칙을 검증하고 DB 접근만 대체
class ExamManagementServiceTest {
    final CourseRepository courses = mock(CourseRepository.class);
    final ExamRepository exams = mock(ExamRepository.class);
    final QuestionRepository questions = mock(QuestionRepository.class);
    final QuestionChoiceRepository choices = mock(QuestionChoiceRepository.class);
    final ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
    final ExamAnswerRepository answers = mock(ExamAnswerRepository.class);
    final ExamConfigurationValidator validator = new ExamConfigurationValidator();
    final ExamService examService = new ExamService(courses, exams, questions, choices, validator);
    final QuestionService service = new QuestionService(examService, questions, choices, attempts, answers, validator);
    Exam exam;
    Question question;
    List<QuestionChoice> items;

    @BeforeEach void setup() {
        exam = exam(); question = question(exam, 1, QuestionType.SINGLE_CHOICE); items = choices(question);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(exam.getCourse()));
        when(courses.existsById(1L)).thenReturn(true);
        when(exams.findByCourseId(1L)).thenReturn(Optional.of(exam));
        when(questions.findByIdAndExamId(1L, 10L)).thenReturn(Optional.of(question));
        when(questions.findByExamIdOrderBySortOrderAsc(10L)).thenReturn(List.of(question));
        when(choices.findByQuestionIdOrderBySortOrderAsc(1L)).thenReturn(items);
        when(choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(10L)).thenReturn(items);
        for (var choice : items) when(choices.findByIdAndQuestionId(choice.getId(), 1L)).thenReturn(Optional.of(choice));
        doAnswer(call -> { Exam value = call.getArgument(0); AssignmentFixtures.id(value, 10L); return value; })
                .when(exams).saveAndFlush(any());
        doAnswer(call -> { Question value = call.getArgument(0); AssignmentFixtures.id(value, 2L); return value; })
                .when(questions).saveAndFlush(any());
        doAnswer(call -> { QuestionChoice value = call.getArgument(0); AssignmentFixtures.id(value, 102L); return value; })
                .when(choices).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "100"})
    void createsExamAtBoundaryWithOneAttempt(String score) {
        var result = examService.create(1L, new ExamCreateRequest("시험", new BigDecimal(score), 1));
        assertThat(result.examId()).isEqualTo(10L);
        assertThat(result.passingScore()).isEqualByComparingTo(score);
        assertThat(result.maxAttempts()).isEqualTo(1);
        assertThat(result.questionCount()).isZero();
        var order = inOrder(courses, exams);
        order.verify(courses).findByIdForUpdate(1L);
        order.verify(exams).existsByCourseId(1L);
    }

    @Test void duplicateExamRejectedBeforeSave() {
        when(exams.existsByCourseId(1L)).thenReturn(true);
        error(() -> examService.create(1L, new ExamCreateRequest("시험", BigDecimal.TEN, 1)), ErrorCode.DUPLICATE_EXAM);
        verify(exams, never()).saveAndFlush(any());
    }

    @Test void missingCourseAndExamUseDifferent404Codes() {
        error(() -> examService.create(99L, new ExamCreateRequest("시험", BigDecimal.TEN, 1)), ErrorCode.COURSE_NOT_FOUND);
        error(() -> service.get(99L, 1L), ErrorCode.COURSE_NOT_FOUND);
        when(exams.findByCourseId(1L)).thenReturn(Optional.empty());
        error(() -> examService.get(1L), ErrorCode.EXAM_NOT_FOUND);
        error(() -> service.get(1L, 1L), ErrorCode.EXAM_NOT_FOUND);
    }

    @Test void getsRawTotalAndUpdatesSettingsWithoutChangingCourse() {
        var second = question(exam, 2, QuestionType.MULTIPLE_CHOICE);
        second.update("문제2", second.getQuestionType(), new BigDecimal("20.50"), 2);
        when(questions.findByExamIdOrderBySortOrderAsc(10L)).thenReturn(List.of(question, second));
        var response = examService.get(1L);
        assertThat(response.questionCount()).isEqualTo(2);
        assertThat(response.totalQuestionScore()).isEqualByComparingTo("30.50");
        var patch = new ExamPatchRequest(); patch.setPassingScore(new BigDecimal("70")); patch.setMaxAttempts(2);
        response = examService.update(1L, patch);
        assertThat(response.title()).isEqualTo("시험");
        assertThat(response.courseId()).isEqualTo(1L);
        assertThat(response.passingScore()).isEqualByComparingTo("70");
    }

    @ParameterizedTest @EnumSource(CourseStatus.class)
    void doesNotInventCourseStatusEditingRestrictions(CourseStatus status) {
        exam.getCourse().changeStatus(status);
        var patch = new ExamPatchRequest(); patch.setTitle("새 제목");
        assertThat(examService.update(1L, patch).title()).isEqualTo("새 제목");
    }

    @ParameterizedTest @EnumSource(QuestionType.class)
    void createsQuestionAndChoicesTogether(QuestionType type) {
        var request = new QuestionCreateRequest("새 문제", type, BigDecimal.TEN, 2,
                List.of(new ChoiceCreateRequest("TRUE", true, 1), new ChoiceCreateRequest("FALSE", false, 2)));
        var response = service.create(1L, request);
        assertThat(response.questionId()).isEqualTo(2L);
        assertThat(response.choices()).hasSize(2);
        assertThat(response.questionType()).isEqualTo(type);
        verify(questions).saveAndFlush(any()); verify(choices).saveAllAndFlush(any());
    }

    @Test void invalidCreationNeverPersistsQuestion() {
        error(() -> service.create(1L, new QuestionCreateRequest("문제", QuestionType.SINGLE_CHOICE, BigDecimal.TEN, 2,
                List.of(new ChoiceCreateRequest("A", false, 1)))), ErrorCode.INVALID_QUESTION_CONFIGURATION);
        verify(questions, never()).saveAndFlush(any());
        error(() -> service.create(1L, new QuestionCreateRequest("문제", QuestionType.SINGLE_CHOICE, BigDecimal.TEN, 2,
                List.of(new ChoiceCreateRequest("A", true, 1), new ChoiceCreateRequest("B", false, 1)))),
                ErrorCode.DUPLICATE_CHOICE_SORT_ORDER);
    }

    @Test void duplicateQuestionOrderFails() {
        when(questions.existsByExamIdAndSortOrder(10L, 1)).thenReturn(true);
        error(() -> service.create(1L, new QuestionCreateRequest("문제", QuestionType.SINGLE_CHOICE, BigDecimal.TEN, 1,
                List.of(new ChoiceCreateRequest("A", true, 1)))), ErrorCode.DUPLICATE_QUESTION_SORT_ORDER);
        when(questions.existsByExamIdAndSortOrderAndIdNot(10L, 2, 1L)).thenReturn(true);
        var patch = new QuestionPatchRequest(); patch.setSortOrder(2);
        error(() -> service.update(1L, 1L, patch), ErrorCode.DUPLICATE_QUESTION_SORT_ORDER);
    }

    @Test void nestedMembershipChecksRejectForeignQuestionAndChoice() {
        error(() -> service.get(1L, 999L), ErrorCode.QUESTION_NOT_FOUND);
        error(() -> service.getChoice(1L, 1L, 999L), ErrorCode.QUESTION_CHOICE_NOT_FOUND);
        error(() -> service.updateChoice(1L, 1L, 999L, new ChoicePatchRequest()), ErrorCode.QUESTION_CHOICE_NOT_FOUND);
        error(() -> service.deleteChoice(1L, 1L, 999L), ErrorCode.QUESTION_CHOICE_NOT_FOUND);
    }

    @Test void questionPatchCanAtomicallySwapSingleCorrectAnswerAndChangeType() {
        var patch = new QuestionPatchRequest(); patch.setQuestionText("수정"); patch.setQuestionType(QuestionType.TRUE_FALSE);
        patch.setCorrectChoiceIds(List.of(101L)); patch.setScore(new BigDecimal("20"));
        var response = service.update(1L, 1L, patch);
        assertThat(response.questionType()).isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(response.choices().get(0).correct()).isFalse();
        assertThat(response.choices().get(1).correct()).isTrue();
        assertThat(response.score()).isEqualByComparingTo("20");
        assertThat(response.choices()).extracting(ChoiceAdminResponse::choiceId).containsExactly(100L, 101L);
    }

    @ParameterizedTest @ValueSource(strings = {"duplicate", "foreign", "empty"})
    void invalidCorrectIdsRejected(String kind) {
        var patch = new QuestionPatchRequest();
        patch.setCorrectChoiceIds(switch (kind) { case "duplicate" -> List.of(100L, 100L); case "foreign" -> List.of(999L); default -> List.of(); });
        error(() -> service.update(1L, 1L, patch), ErrorCode.INVALID_QUESTION_CONFIGURATION);
        verify(questions, never()).flush();
    }

    @Test void incompatibleTypeChangeRejected() {
        question.update("복수", QuestionType.MULTIPLE_CHOICE, BigDecimal.TEN, 1);
        items.forEach(c -> c.changeCorrect(true));
        var patch = new QuestionPatchRequest(); patch.setQuestionType(QuestionType.SINGLE_CHOICE);
        error(() -> service.update(1L, 1L, patch), ErrorCode.INVALID_QUESTION_CONFIGURATION);
        verify(questions, never()).flush();
    }

    @Test void readListsAreOrderedAndChoicesFetchedInBulk() {
        var second = question(exam, 2, QuestionType.SINGLE_CHOICE);
        when(questions.findByExamIdOrderBySortOrderAsc(10L)).thenReturn(List.of(question, second));
        assertThat(service.getAll(1L)).extracting(QuestionAdminResponse::questionId).containsExactly(1L, 2L);
        verify(choices).findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(10L);
        verify(choices, never()).findByQuestionIdOrderBySortOrderAsc(anyLong());
        assertThat(service.get(1L, 1L).choices()).extracting(ChoiceAdminResponse::sortOrder).containsExactly(1, 2);
    }

    @Test void choiceCrudPreservesValidConfiguration() {
        assertThat(service.createChoice(1L, 1L, new ChoiceCreateRequest("C", false, 3)).choiceId()).isEqualTo(102L);
        var patch = new ChoicePatchRequest(); patch.setChoiceText("오답 변경"); patch.setSortOrder(3);
        assertThat(service.updateChoice(1L, 1L, 101L, patch).choiceText()).isEqualTo("오답 변경");
        assertThat(service.getChoices(1L, 1L)).hasSize(2);
        assertThat(service.getChoice(1L, 1L, 100L).correct()).isTrue();
        service.deleteChoice(1L, 1L, 101L);
        verify(choices).delete(items.get(1));
    }

    @Test void choiceCreationRejectsDuplicateOrderOrInvalidAnswerCount() {
        error(() -> service.createChoice(1L, 1L, new ChoiceCreateRequest("C", false, 1)), ErrorCode.DUPLICATE_CHOICE_SORT_ORDER);
        error(() -> service.createChoice(1L, 1L, new ChoiceCreateRequest("C", true, 3)), ErrorCode.INVALID_QUESTION_CONFIGURATION);
        verify(choices, never()).saveAndFlush(any());
    }

    @Test void choiceUpdateCannotRemoveOnlyCorrectAnswer() {
        var patch = new ChoicePatchRequest(); patch.setCorrect(false);
        error(() -> service.updateChoice(1L, 1L, 100L, patch), ErrorCode.INVALID_QUESTION_CONFIGURATION);
        verify(choices, never()).flush();
    }

    @Test void choiceDeleteCannotLeaveInvalidQuestion() {
        error(() -> service.deleteChoice(1L, 1L, 100L), ErrorCode.INVALID_QUESTION_CONFIGURATION);
        question.update("참거짓", QuestionType.TRUE_FALSE, BigDecimal.TEN, 1);
        error(() -> service.deleteChoice(1L, 1L, 101L), ErrorCode.INVALID_QUESTION_CONFIGURATION);
        verify(choices, never()).delete(any());
    }

    @Test void questionDeleteRemovesChildrenBeforeParent() {
        service.delete(1L, 1L);
        var order = inOrder(choices, questions);
        order.verify(choices).deleteAll(items); order.verify(choices).flush();
        order.verify(questions).delete(question); order.verify(questions).flush();
    }

    @Test void attemptHistoryBlocksQuestionAndChoiceDeletion() {
        when(attempts.existsByExamId(10L)).thenReturn(true);
        error(() -> service.delete(1L, 1L), ErrorCode.EXAM_HISTORY_DELETE_CONFLICT);
        error(() -> service.deleteChoice(1L, 1L, 101L), ErrorCode.EXAM_HISTORY_DELETE_CONFLICT);
        verify(choices, never()).delete(any()); verify(choices, never()).deleteAll(any()); verify(questions, never()).delete(any());
    }

    @Test void directAnswerReferencesAlsoBlockDeletion() {
        when(answers.existsByQuestionId(1L)).thenReturn(true);
        error(() -> service.delete(1L, 1L), ErrorCode.EXAM_HISTORY_DELETE_CONFLICT);
        when(answers.referencesChoice(101L)).thenReturn(true);
        error(() -> service.deleteChoice(1L, 1L, 101L), ErrorCode.EXAM_HISTORY_DELETE_CONFLICT);
        when(answers.existsByQuestionId(1L)).thenReturn(false);
        error(() -> service.delete(1L, 1L), ErrorCode.EXAM_HISTORY_DELETE_CONFLICT);
    }

    @Test void questionReorderUsesTwoDisjointPositivePhases() {
        var second = question(exam, 2, QuestionType.SINGLE_CHOICE);
        when(questions.findByExamIdOrderBySortOrderAsc(10L)).thenReturn(List.of(question, second));
        List<List<Integer>> flushed = new ArrayList<>();
        doAnswer(call -> { flushed.add(List.of(question.getSortOrder(), second.getSortOrder())); return null; }).when(questions).flush();
        var result = service.reorder(1L, new QuestionOrderRequest(List.of(2L, 1L)));
        assertThat(flushed).containsExactly(List.of(4, 3), List.of(2, 1));
        assertThat(result).extracting(QuestionAdminResponse::questionId).containsExactly(2L, 1L);
    }

    @Test void choiceReorderUsesTwoDisjointPositivePhases() {
        List<List<Integer>> flushed = new ArrayList<>();
        doAnswer(call -> { flushed.add(items.stream().map(QuestionChoice::getSortOrder).toList()); return null; }).when(choices).flush();
        var result = service.reorderChoices(1L, 1L, new ChoiceOrderRequest(List.of(101L, 100L)));
        assertThat(flushed).containsExactly(List.of(4, 3), List.of(2, 1));
        assertThat(result).extracting(ChoiceAdminResponse::choiceId).containsExactly(101L, 100L);
    }

    @Test void duplicateMissingAndForeignReorderIdsRejected() {
        for (var ids : List.of(List.of(1L, 1L), List.<Long>of(), List.of(99L))) {
            error(() -> service.reorder(1L, new QuestionOrderRequest(ids)), ErrorCode.INVALID_QUESTION_ORDER);
        }
        for (var ids : List.of(List.of(100L, 100L), List.of(100L), List.of(100L, 999L))) {
            error(() -> service.reorderChoices(1L, 1L, new ChoiceOrderRequest(ids)), ErrorCode.INVALID_CHOICE_ORDER);
        }
        verify(questions, never()).flush(); verify(choices, never()).flush();
    }

    @Test void temporaryOrderOverflowRejected() {
        question.changeSortOrder(Integer.MAX_VALUE);
        error(() -> service.reorder(1L, new QuestionOrderRequest(List.of(1L))), ErrorCode.EXAM_ORDER_LIMIT_EXCEEDED);
        items.getFirst().changeSortOrder(Integer.MAX_VALUE);
        error(() -> service.reorderChoices(1L, 1L, new ChoiceOrderRequest(List.of(100L, 101L))), ErrorCode.EXAM_ORDER_LIMIT_EXCEEDED);
    }

    @Test void validatesWholeExamWithoutStartingAttempt() {
        assertThat(examService.validateConfiguration(1L).totalQuestionScore()).isEqualByComparingTo("10");
        when(questions.findByExamIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        error(() -> examService.validateConfiguration(1L), ErrorCode.INVALID_EXAM_CONFIGURATION);
        verifyNoInteractions(attempts, answers);
    }

    private void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
}

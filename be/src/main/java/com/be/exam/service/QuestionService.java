package com.be.exam.service;

import com.be.exam.dto.*;
import com.be.exam.entity.*;
import com.be.exam.repository.*;
import com.be.global.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 문항과 선택지를 하나의 구성 단위로 관리; 별도 Strategy/ChoiceService 불필요
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {
    private final ExamService exams;
    private final QuestionRepository questions;
    private final QuestionChoiceRepository choices;
    private final ExamAttemptRepository attempts;
    private final ExamAnswerRepository answers;
    private final ExamConfigurationValidator validator;

    // 유효한 선택지 집합을 필수로 받아 문항과 함께 저장
    @Transactional
    public QuestionAdminResponse create(@NotNull @Positive Long courseId, @NotNull @Valid QuestionCreateRequest request) {
        Exam exam = exams.requireExam(courseId, true);
        if (questions.existsByExamIdAndSortOrder(exam.getId(), request.sortOrder())) duplicateQuestionOrder();
        var question = Question.create(exam, request.questionText().trim(), request.questionType(), request.score(), request.sortOrder());
        var items = request.choices().stream().map(c -> QuestionChoice.create(question, c.choiceText().trim(), c.correct(), c.sortOrder())).toList();
        checkChoiceOrders(items);
        validator.validateQuestion(question, items);
        questions.saveAndFlush(question);
        choices.saveAllAndFlush(items);
        return QuestionAdminResponse.from(question, items);
    }

    public List<QuestionAdminResponse> getAll(@NotNull @Positive Long courseId) {
        Exam exam = exams.requireExam(courseId, false);
        var byQuestion = choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(exam.getId()).stream()
                .collect(Collectors.groupingBy(c -> c.getQuestion().getId()));
        return questions.findByExamIdOrderBySortOrderAsc(exam.getId()).stream()
                .map(q -> QuestionAdminResponse.from(q, byQuestion.getOrDefault(q.getId(), List.of()))).toList();
    }

    public QuestionAdminResponse get(@NotNull @Positive Long courseId, @NotNull @Positive Long questionId) {
        Question question = find(courseId, questionId, false);
        return QuestionAdminResponse.from(question, choiceList(question));
    }

    // correctChoiceIds가 있으면 기존 Choice ID를 유지한 채 정답 집합을 원자적으로 교체
    @Transactional
    public QuestionAdminResponse update(@NotNull @Positive Long courseId, @NotNull @Positive Long questionId,
            @NotNull @Valid QuestionPatchRequest request) {
        Question question = find(courseId, questionId, true);
        int order = request.getSortOrder() == null ? question.getSortOrder() : request.getSortOrder();
        if (questions.existsByExamIdAndSortOrderAndIdNot(question.getExam().getId(), order, questionId)) duplicateQuestionOrder();
        var items = choiceList(question);
        if (request.getCorrectChoiceIds() != null) {
            var ids = request.getCorrectChoiceIds();
            Set<Long> targets = new HashSet<>(ids);
            Set<Long> existing = items.stream().map(QuestionChoice::getId).collect(Collectors.toSet());
            if (targets.size() != ids.size() || !existing.containsAll(targets)) {
                throw new BusinessException(ErrorCode.INVALID_QUESTION_CONFIGURATION);
            }
            items.forEach(c -> c.changeCorrect(targets.contains(c.getId())));
        }
        question.update(request.getQuestionText() == null ? question.getQuestionText() : request.getQuestionText().trim(),
                request.getQuestionType() == null ? question.getQuestionType() : request.getQuestionType(),
                request.getScore() == null ? question.getScore() : request.getScore(), order);
        validator.validateQuestion(question, items);
        questions.flush();
        return QuestionAdminResponse.from(question, items);
    }

    // 응시 이력이 없는 문항만 선택지부터 명시적으로 삭제; FK RESTRICT 유지
    @Transactional
    public void delete(@NotNull @Positive Long courseId, @NotNull @Positive Long questionId) {
        Question question = find(courseId, questionId, true);
        requireNoAttempts(question);
        if (answers.existsByQuestionId(questionId)) historyConflict();
        var items = choiceList(question);
        for (var choice : items) if (answers.referencesChoice(choice.getId())) historyConflict();
        choices.deleteAll(items);
        choices.flush();
        questions.delete(question);
        questions.flush();
    }

    @Transactional
    public List<QuestionAdminResponse> reorder(@NotNull @Positive Long courseId, @NotNull @Valid QuestionOrderRequest request) {
        Exam exam = exams.requireExam(courseId, true);
        var current = questions.findByExamIdOrderBySortOrderAsc(exam.getId());
        var byId = current.stream().collect(Collectors.toMap(Question::getId, q -> q));
        checkFullOrder(request.questionIds(), byId.keySet(), ErrorCode.INVALID_QUESTION_ORDER);
        int max = current.stream().mapToInt(Question::getSortOrder).max().orElse(0);
        checkOrderCapacity(max, current.size());
        for (int i = 0; i < current.size(); i++) byId.get(request.questionIds().get(i)).changeSortOrder(max + i + 1);
        questions.flush();
        for (int i = 0; i < current.size(); i++) byId.get(request.questionIds().get(i)).changeSortOrder(i + 1);
        questions.flush();
        var byQuestion = choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(exam.getId()).stream()
                .collect(Collectors.groupingBy(c -> c.getQuestion().getId()));
        return request.questionIds().stream().map(id -> QuestionAdminResponse.from(byId.get(id), byQuestion.getOrDefault(id, List.of()))).toList();
    }

    public List<ChoiceAdminResponse> getChoices(@NotNull @Positive Long courseId, @NotNull @Positive Long questionId) {
        return choiceList(find(courseId, questionId, false)).stream().map(ChoiceAdminResponse::from).toList();
    }

    public ChoiceAdminResponse getChoice(@NotNull @Positive Long courseId, @NotNull @Positive Long questionId,
            @NotNull @Positive Long choiceId) {
        find(courseId, questionId, false);
        return ChoiceAdminResponse.from(findChoice(questionId, choiceId));
    }

    @Transactional
    public ChoiceAdminResponse createChoice(@NotNull @Positive Long courseId, @NotNull @Positive Long questionId,
            @NotNull @Valid ChoiceCreateRequest request) {
        Question question = find(courseId, questionId, true);
        var items = new ArrayList<>(choiceList(question));
        var choice = QuestionChoice.create(question, request.choiceText().trim(), request.correct(), request.sortOrder());
        items.add(choice);
        checkChoiceOrders(items);
        validator.validateQuestion(question, items);
        return ChoiceAdminResponse.from(choices.saveAndFlush(choice));
    }

    @Transactional
    public ChoiceAdminResponse updateChoice(@NotNull @Positive Long courseId, @NotNull @Positive Long questionId,
            @NotNull @Positive Long choiceId, @NotNull @Valid ChoicePatchRequest request) {
        Question question = find(courseId, questionId, true);
        QuestionChoice choice = findChoice(questionId, choiceId);
        var items = choiceList(question);
        choice.update(request.getChoiceText() == null ? choice.getChoiceText() : request.getChoiceText().trim(),
                request.getCorrect() == null ? choice.isCorrect() : request.getCorrect(),
                request.getSortOrder() == null ? choice.getSortOrder() : request.getSortOrder());
        checkChoiceOrders(items);
        validator.validateQuestion(question, items);
        choices.flush();
        return ChoiceAdminResponse.from(choice);
    }

    @Transactional
    public void deleteChoice(@NotNull @Positive Long courseId, @NotNull @Positive Long questionId,
            @NotNull @Positive Long choiceId) {
        Question question = find(courseId, questionId, true);
        QuestionChoice choice = findChoice(questionId, choiceId);
        requireNoAttempts(question);
        if (answers.referencesChoice(choiceId)) historyConflict();
        var remaining = choiceList(question).stream().filter(c -> !c.getId().equals(choiceId)).toList();
        validator.validateQuestion(question, remaining);
        choices.delete(choice);
        choices.flush();
    }

    // 기존 콘텐츠와 동일하게 임시 양수 순서 → 최종 1..N 순서, 각 단계 flush
    @Transactional
    public List<ChoiceAdminResponse> reorderChoices(@NotNull @Positive Long courseId, @NotNull @Positive Long questionId,
            @NotNull @Valid ChoiceOrderRequest request) {
        Question question = find(courseId, questionId, true);
        var current = choiceList(question);
        var byId = current.stream().collect(Collectors.toMap(QuestionChoice::getId, c -> c));
        checkFullOrder(request.choiceIds(), byId.keySet(), ErrorCode.INVALID_CHOICE_ORDER);
        int max = current.stream().mapToInt(QuestionChoice::getSortOrder).max().orElse(0);
        checkOrderCapacity(max, current.size());
        for (int i = 0; i < current.size(); i++) byId.get(request.choiceIds().get(i)).changeSortOrder(max + i + 1);
        choices.flush();
        for (int i = 0; i < current.size(); i++) byId.get(request.choiceIds().get(i)).changeSortOrder(i + 1);
        choices.flush();
        return request.choiceIds().stream().map(id -> ChoiceAdminResponse.from(byId.get(id))).toList();
    }

    private Question find(Long courseId, Long questionId, boolean write) {
        Exam exam = exams.requireExam(courseId, write);
        return questions.findByIdAndExamId(questionId, exam.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
    }

    private QuestionChoice findChoice(Long questionId, Long choiceId) {
        return choices.findByIdAndQuestionId(choiceId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_CHOICE_NOT_FOUND));
    }

    private List<QuestionChoice> choiceList(Question question) {
        return choices.findByQuestionIdOrderBySortOrderAsc(question.getId());
    }

    private void checkChoiceOrders(List<QuestionChoice> items) {
        if (items.stream().map(QuestionChoice::getSortOrder).distinct().count() != items.size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_CHOICE_SORT_ORDER);
        }
    }

    private void checkFullOrder(List<Long> ids, Set<Long> existing, ErrorCode code) {
        if (ids.size() != existing.size() || new HashSet<>(ids).size() != ids.size()
                || !existing.equals(new HashSet<>(ids))) throw new BusinessException(code);
    }

    private void checkOrderCapacity(int max, int size) {
        if ((long) max + size > Integer.MAX_VALUE) throw new BusinessException(ErrorCode.EXAM_ORDER_LIMIT_EXCEEDED);
    }

    private void requireNoAttempts(Question question) {
        if (attempts.existsByExamId(question.getExam().getId())) historyConflict();
    }

    private void historyConflict() { throw new BusinessException(ErrorCode.EXAM_HISTORY_DELETE_CONFLICT); }
    private void duplicateQuestionOrder() { throw new BusinessException(ErrorCode.DUPLICATE_QUESTION_SORT_ORDER); }
}

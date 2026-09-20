package com.be.exam;

import com.be.exam.entity.*;
import com.be.exam.enums.QuestionType;
import com.be.exam.service.ExamConfigurationValidator;
import com.be.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import static com.be.exam.ExamFixtures.*;
import static org.assertj.core.api.Assertions.*;

// 저장 및 향후 응시 시작에서 공통으로 적용할 정답 구성 규칙
class ExamConfigurationValidatorTest {
    final ExamConfigurationValidator validator = new ExamConfigurationValidator();

    @ParameterizedTest @EnumSource(QuestionType.class)
    void acceptsOneCorrectChoiceForAllTypes(QuestionType type) {
        var question = question(exam(), 1, type);
        assertThatCode(() -> validator.validateQuestion(question, choices(question))).doesNotThrowAnyException();
    }

    @ParameterizedTest @EnumSource(QuestionType.class)
    void rejectsNoChoicesOrNoCorrectChoices(QuestionType type) {
        var question = question(exam(), 1, type);
        assertThatThrownBy(() -> validator.validateQuestion(question, List.of())).isInstanceOf(BusinessException.class);
        var items = choices(question);
        items.forEach(c -> c.changeCorrect(false));
        assertThatThrownBy(() -> validator.validateQuestion(question, items)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest @EnumSource(value = QuestionType.class, names = {"SINGLE_CHOICE", "TRUE_FALSE"})
    void rejectsMultipleCorrectForSingleAndTrueFalse(QuestionType type) {
        var question = question(exam(), 1, type);
        var items = choices(question);
        items.forEach(c -> c.changeCorrect(true));
        assertThatThrownBy(() -> validator.validateQuestion(question, items)).isInstanceOf(BusinessException.class);
    }

    @Test void multipleAllowsOneOrManyCorrectWithoutMinimumTwoRule() {
        var question = question(exam(), 1, QuestionType.MULTIPLE_CHOICE);
        validator.validateQuestion(question, List.of(choice(question, 1, "A", true, 1)));
        var items = choices(question);
        items.forEach(c -> c.changeCorrect(true));
        validator.validateQuestion(question, items);
    }

    @Test void trueFalseRequiresTwoDistinctLiteralChoices() {
        var question = question(exam(), 1, QuestionType.TRUE_FALSE);
        for (var invalid : List.of(
                List.of(choice(question, 1, "TRUE", true, 1)),
                List.of(choice(question, 1, "TRUE", true, 1), choice(question, 2, "TRUE", false, 2)),
                List.of(choice(question, 1, "A", true, 1), choice(question, 2, "B", false, 2)),
                List.of(choice(question, 1, "TRUE", true, 1), choice(question, 2, "FALSE", false, 2), choice(question, 3, "C", false, 3)))) {
            assertThatThrownBy(() -> validator.validateQuestion(question, invalid)).isInstanceOf(BusinessException.class);
        }
    }

    @Test void emptyExamAndBadSettingsAreNotReady() {
        var exam = exam();
        var question = question(exam, 1, QuestionType.SINGLE_CHOICE);
        assertThatThrownBy(() -> validator.validate(exam, List.of(), List.of())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(null, List.of(question), choices(question))).isInstanceOf(BusinessException.class);
        for (var score : List.of("-1", "101")) {
            exam.update("시험", new BigDecimal(score), 1);
            assertThatThrownBy(() -> validator.validate(exam, List.of(question), choices(question))).isInstanceOf(BusinessException.class);
        }
        exam.update("시험", BigDecimal.TEN, 0);
        assertThatThrownBy(() -> validator.validate(exam, List.of(question), choices(question))).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1"})
    void nonPositiveRawScoreRejected(String score) {
        var question = question(exam(), 1, QuestionType.SINGLE_CHOICE);
        question.update("문제", question.getQuestionType(), new BigDecimal(score), 1);
        assertThatThrownBy(() -> validator.validateQuestion(question, choices(question))).isInstanceOf(BusinessException.class);
    }

    @Test void allQuestionsMustBeValidAndRawScoresNeedNotTotalHundred() {
        var exam = exam();
        var first = question(exam, 1, QuestionType.SINGLE_CHOICE);
        var second = question(exam, 2, QuestionType.MULTIPLE_CHOICE);
        List<QuestionChoice> items = new ArrayList<>(choices(first));
        items.addAll(choices(second));
        validator.validate(exam, List.of(first, second), items);
        assertThatThrownBy(() -> validator.validate(exam, List.of(first, second), choices(first)))
                .isInstanceOf(BusinessException.class);
    }
}

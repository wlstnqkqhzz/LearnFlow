package com.be.exam.service;

import com.be.exam.entity.*;
import com.be.global.exception.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 관리 API와 향후 응시 시작에서 재사용할 시험 구성 검사 (채점은 수행하지 않음)
@Component
public class ExamConfigurationValidator {
    public void validate(Exam exam, List<Question> questions, List<QuestionChoice> choices) {
        if (exam == null || exam.getPassingScore() == null || exam.getPassingScore().signum() < 0
                || exam.getPassingScore().compareTo(new BigDecimal("100")) > 0
                || exam.getMaxAttempts() < 1 || questions.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXAM_CONFIGURATION);
        }
        var byQuestion = choices.stream().collect(Collectors.groupingBy(c -> c.getQuestion().getId()));
        for (Question question : questions) validateQuestion(question, byQuestion.getOrDefault(question.getId(), List.of()));
        BigDecimal total = questions.stream().map(Question::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) throw new BusinessException(ErrorCode.INVALID_EXAM_CONFIGURATION);
    }

    // 항상 최종 선택지 집합을 검증하므로 부분 수정 중간 상태는 DB에 남기지 않음
    public void validateQuestion(Question question, List<QuestionChoice> choices) {
        if (question.getQuestionType() == null || question.getScore() == null || question.getScore().signum() <= 0
                || choices.isEmpty()) invalid();
        long correct = choices.stream().filter(QuestionChoice::isCorrect).count();
        boolean valid = switch (question.getQuestionType()) {
            case SINGLE_CHOICE -> correct == 1;
            case MULTIPLE_CHOICE -> correct >= 1;
            case TRUE_FALSE -> choices.size() == 2 && correct == 1
                    && choices.stream().map(QuestionChoice::getChoiceText).collect(Collectors.toSet())
                    .equals(Set.of("TRUE", "FALSE"));
        };
        if (!valid) invalid();
    }

    private void invalid() { throw new BusinessException(ErrorCode.INVALID_QUESTION_CONFIGURATION); }
}

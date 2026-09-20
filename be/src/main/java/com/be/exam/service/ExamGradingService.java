package com.be.exam.service;
import com.be.exam.entity.*;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 부분점수 없이 집합 일치로 채점; 미응답도 분모에 포함
@Component
public class ExamGradingService {
    public BigDecimal grade(List<Question> questions, List<QuestionChoice> choices, List<ExamAnswer> answers) {
        var correctByQuestion = choices.stream().filter(QuestionChoice::isCorrect).collect(
                Collectors.groupingBy(c -> c.getQuestion().getId(),
                        Collectors.mapping(QuestionChoice::getId, Collectors.toSet())));
        var byQuestion = answers.stream().collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a));
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (var question : questions) {
            total = total.add(question.getScore());
            var answer = byQuestion.get(question.getId());
            if (answer == null) continue;
            var selected = answer.getSelectedChoices().stream().map(QuestionChoice::getId).collect(Collectors.toSet());
            boolean correct = selected.equals(correctByQuestion.getOrDefault(question.getId(), Set.of()));
            answer.grade(correct);
            earned = earned.add(answer.getEarnedScore());
        }
        if (total.signum() <= 0) throw new com.be.global.exception.BusinessException(
                com.be.global.exception.ErrorCode.INVALID_EXAM_CONFIGURATION);
        return earned.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP);
    }
}

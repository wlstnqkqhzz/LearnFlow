package com.be.exam;

import com.be.exam.dto.*;
import com.be.exam.enums.QuestionType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

// 관리자 JSON의 정답 계약과 로그용 문자열의 비노출을 구분하여 검증
class ExamDtoRedactionTest {
    @Test
    void nestedQuestionRequestDoesNotPrintAnswerChoices() {
        var choice = new ChoiceCreateRequest("sensitive-answer-text", true, 1);
        var question = new QuestionCreateRequest("문제", QuestionType.SINGLE_CHOICE, BigDecimal.TEN, 1, List.of(choice));
        assertThat(choice.toString()).contains("REDACTED").doesNotContain("sensitive-answer-text", "correct=true");
        assertThat(question.toString()).doesNotContain("sensitive-answer-text", "correct=true");
    }

    @Test
    void adminJsonKeepsCorrectButNestedToStringDoesNotPrintIt() {
        var choice = new ChoiceAdminResponse(1L, "sensitive-answer-text", true, 1);
        var question = new QuestionAdminResponse(1L, QuestionType.SINGLE_CHOICE, "문제", BigDecimal.TEN, 1, List.of(choice));
        assertThat(question.toString()).doesNotContain("sensitive-answer-text", "correct=true");
        var json = new ObjectMapper().writeValueAsString(question);
        assertThat(json).contains("\"correct\":true", "sensitive-answer-text");
    }
}

package com.be.exam.dto;
import com.be.exam.entity.*;
import com.be.exam.enums.QuestionType;
import java.math.BigDecimal;
import java.util.*;
// 선택지 정답을 포함하는 관리자 전용 문항 DTO
public record QuestionAdminResponse(Long questionId, QuestionType questionType, String questionText,
        BigDecimal score, int sortOrder, List<ChoiceAdminResponse> choices) {
    public static QuestionAdminResponse from(Question question, List<QuestionChoice> choices) {
        return new QuestionAdminResponse(question.getId(), question.getQuestionType(), question.getQuestionText(),
                question.getScore(), question.getSortOrder(), choices.stream()
                .sorted(Comparator.comparingInt(QuestionChoice::getSortOrder)).map(ChoiceAdminResponse::from).toList());
    }
}

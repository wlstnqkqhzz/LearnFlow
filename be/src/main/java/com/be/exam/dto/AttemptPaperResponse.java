package com.be.exam.dto;
import com.be.exam.enums.QuestionType;
import java.math.BigDecimal;
import java.util.List;
// 관리자 DTO와 분리된 응시용 화이트리스트 응답
public record AttemptPaperResponse(Long attemptId, String title, List<QuestionItem> questions) {
    public record QuestionItem(Long questionId, QuestionType questionType, String questionText,
            BigDecimal score, int sortOrder, List<ChoiceItem> choices, List<Long> selectedChoiceIds) {}
    public record ChoiceItem(Long choiceId, String choiceText, int sortOrder) {}
}

package com.be.exam.dto;
import com.be.exam.entity.QuestionChoice;
// 정답을 포함하는 관리자 전용 응답; 향후 응시 API에서 재사용 금지
public record ChoiceAdminResponse(Long choiceId, String choiceText, boolean correct, int sortOrder) {
    // 관리자 JSON 계약은 유지하되 로그용 문자열에 정답·선택지 본문을 남기지 않음
    @Override
    public String toString() { return "ChoiceAdminResponse[REDACTED]"; }

    public static ChoiceAdminResponse from(QuestionChoice choice) {
        return new ChoiceAdminResponse(choice.getId(), choice.getChoiceText(), choice.isCorrect(), choice.getSortOrder());
    }
}

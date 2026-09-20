package com.be.exam.dto;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import lombok.Getter;

// 선택지 부분 수정; 변경 후에도 문항 전체 정답 구성을 검증
@Getter
public class ChoicePatchRequest {
    @Size(max = 1000) @Pattern(regexp = "(?s).*\\S.*")
    private String choiceText;
    // 정답 여부 변경 후 문항 전체 정답 수를 다시 검증
    private Boolean correct;
    @Positive
    private Integer sortOrder;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setChoiceText(String value) { this.choiceText = value; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setCorrect(Boolean value) { this.correct = value; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setSortOrder(Integer value) { this.sortOrder = value; }
}

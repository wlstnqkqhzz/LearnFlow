package com.be.exam.dto;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import com.be.exam.enums.QuestionType;
import lombok.Getter;

// 유형과 정답 집합을 한 트랜잭션에서 변경하여 유효한 최종 구성만 저장
@Getter
public class QuestionPatchRequest {
    // 변경할 문제 내용
    @Pattern(regexp = "(?s).*\\S.*")
    private String questionText;
    // 기존 선택지 구성과 함께 검증할 새 유형
    private QuestionType questionType;
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 2)
    private BigDecimal score;
    @Positive
    private Integer sortOrder;
    // 생략 시 정답 유지, 전달 시 해당 문항의 정답 ID 집합으로 교체
    private List<@NotNull @Positive Long> correctChoiceIds;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setQuestionText(String value) { this.questionText = value; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setQuestionType(QuestionType value) { this.questionType = value; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setScore(BigDecimal value) { this.score = value; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setSortOrder(Integer value) { this.sortOrder = value; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setCorrectChoiceIds(List<@NotNull @Positive Long> value) { this.correctChoiceIds = value; }
}

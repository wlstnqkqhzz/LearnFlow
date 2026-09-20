package com.be.exam.dto;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Getter;

// 시험 부분 수정: 생략은 유지, 명시적 null은 거절
@Getter
public class ExamPatchRequest {
    @Size(max = 200) @Pattern(regexp = "(?s).*\\S.*")
    private String title;
    @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
    private BigDecimal passingScore;
    @Min(1)
    private Integer maxAttempts;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTitle(String value) { this.title = value; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setPassingScore(BigDecimal value) { this.passingScore = value; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setMaxAttempts(Integer value) { this.maxAttempts = value; }
}

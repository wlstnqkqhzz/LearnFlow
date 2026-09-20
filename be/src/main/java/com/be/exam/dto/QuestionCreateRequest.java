package com.be.exam.dto;

import com.be.exam.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

// 문항과 선택지를 함께 생성하여 정답 없는 중간 상태가 저장되지 않도록 함
public record QuestionCreateRequest(@NotBlank String questionText, @NotNull QuestionType questionType,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 2) BigDecimal score,
        @NotNull @Positive Integer sortOrder, @NotEmpty List<@NotNull @Valid ChoiceCreateRequest> choices) {}

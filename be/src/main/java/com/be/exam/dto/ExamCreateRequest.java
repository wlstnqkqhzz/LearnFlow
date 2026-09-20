package com.be.exam.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

// 시험 제목, 정규화 합격 기준, 최대 응시 횟수
public record ExamCreateRequest(@NotBlank @Size(max = 200) String title,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal passingScore,
        @NotNull @Min(1) Integer maxAttempts) {}

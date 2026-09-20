package com.be.exam.dto;

import jakarta.validation.constraints.*;

// 문항 생성 시 중첩 입력과 선택지 단독 생성에 공통 사용
public record ChoiceCreateRequest(@NotBlank @Size(max = 1000) String choiceText,
        @NotNull Boolean correct, @NotNull @Positive Integer sortOrder) {}

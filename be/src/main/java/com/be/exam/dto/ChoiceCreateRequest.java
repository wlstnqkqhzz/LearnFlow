package com.be.exam.dto;

import jakarta.validation.constraints.*;

// 문항 생성 시 중첩 입력과 선택지 단독 생성에 공통 사용
public record ChoiceCreateRequest(@NotBlank @Size(max = 1000) String choiceText,
        @NotNull Boolean correct, @NotNull @Positive Integer sortOrder) {
    // 중첩 문항 요청의 toString에서도 정답 여부가 노출되지 않도록 보호
    @Override
    public String toString() { return "ChoiceCreateRequest[REDACTED]"; }
}

package com.be.exam.dto;
import jakarta.validation.constraints.*;
import java.util.List;
// 단일/참거짓은 1개, 복수 선택은 빈 목록을 포함한 집합 허용
public record AnswerSaveRequest(@NotNull List<@NotNull @Positive Long> selectedChoiceIds) {}

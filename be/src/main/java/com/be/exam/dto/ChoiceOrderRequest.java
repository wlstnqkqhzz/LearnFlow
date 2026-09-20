package com.be.exam.dto;
import jakarta.validation.constraints.*;
import java.util.List;
// 해당 문항의 전체 선택지 ID를 희망 순서대로 전달
public record ChoiceOrderRequest(@NotNull List<@NotNull @Positive Long> choiceIds) {}

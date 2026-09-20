package com.be.exam.dto;
import jakarta.validation.constraints.*;
import java.util.List;
// 해당 시험의 전체 문항 ID를 희망 순서대로 전달
public record QuestionOrderRequest(@NotNull List<@NotNull @Positive Long> questionIds) {}

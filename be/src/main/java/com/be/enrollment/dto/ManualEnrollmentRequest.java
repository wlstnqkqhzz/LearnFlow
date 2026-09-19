package com.be.enrollment.dto;

import jakarta.validation.constraints.*;

// 수동 배정 대상만 받으며 출처·상태·마감일은 서버에서 결정
public record ManualEnrollmentRequest(@NotNull @Positive Long memberId) {}

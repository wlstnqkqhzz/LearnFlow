package com.be.retraining.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

// 생성/전체 설정 교체 DTO. null로 필수 설정을 제거할 수 없다.
public record RetrainingPolicyRequest(@NotNull @Positive Long sourceCourseId,
        @NotBlank @Size(max = 150) String baseTitle,
        @NotNull Boolean enabled, @NotNull Boolean autoCreate, @NotNull Boolean autoOpen,
        @NotNull @Min(1) @Max(1200) Integer intervalMonths, @NotNull LocalDate firstStartDate,
        @NotNull @Min(1) @Max(3660) Integer durationDays,
        @NotNull @Min(0) @Max(3660) Integer generationLeadDays) {}

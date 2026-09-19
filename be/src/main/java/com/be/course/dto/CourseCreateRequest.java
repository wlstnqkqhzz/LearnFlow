package com.be.course.dto;

import com.be.course.enums.CourseType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

// 교육과정 생성 입력: 상태는 받지 않으며 수료 진도율 기본값은 100
public record CourseCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 16383) String description,
        @NotNull CourseType courseType,
        LocalDate startDate,
        LocalDate endDate,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal passingProgressRate,
        @Positive Long instructorId
) {
    public CourseCreateRequest {
        title = title == null ? null : title.trim();
        passingProgressRate = passingProgressRate == null ? new BigDecimal("100.00") : passingProgressRate;
    }
}

package com.be.enrollment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

// DECIMAL(5,2)에 정확히 저장 가능한 진도율만 허용; 완료 시각은 입력받지 않음
public record ContentProgressUpdateRequest(
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal progressRate) {
}

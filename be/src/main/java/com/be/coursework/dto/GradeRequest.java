package com.be.coursework.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record GradeRequest(
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal score,
    @Size(max = 10000) String feedback) {}

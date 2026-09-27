package com.be.coursework.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record AssignmentCreateRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 10000) String description,
    @NotNull Boolean required,
    @NotNull LocalDate dueDate,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal passingScore,
    @Min(1) int sortOrder) {}

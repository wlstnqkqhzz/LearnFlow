package com.be.coursework.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record AssignmentOrderRequest(@NotNull List<@NotNull @Positive Long> assignmentIds) {}

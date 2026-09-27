package com.be.coursework.dto;
import com.be.coursework.enums.AssignmentSubmissionType;
import jakarta.validation.constraints.*;
public record SubmissionRequest(@NotNull AssignmentSubmissionType submissionType, @NotBlank @Size(max = 10000) String content) {}

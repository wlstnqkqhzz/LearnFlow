package com.be.coursework.dto;
import com.be.coursework.enums.AssignmentSubmissionStatus;
import jakarta.validation.constraints.*;
public record SubmissionSearchRequest(@Positive Long assignmentId, @Positive Long enrollmentId, @Positive Long memberId,
    AssignmentSubmissionStatus status, @Min(0) Integer page, @Min(1) @Max(100) Integer size) {
    public SubmissionSearchRequest { page = page == null ? 0 : page; size = size == null ? 20 : size; }
}

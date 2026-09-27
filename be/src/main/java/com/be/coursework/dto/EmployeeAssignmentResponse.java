package com.be.coursework.dto;
import com.be.coursework.enums.AssignmentSubmissionStatus;
import java.time.LocalDate;
public record EmployeeAssignmentResponse(AssignmentResponse assignment, AssignmentSubmissionStatus status,
    LocalDate effectiveDueDate, boolean submittable, boolean resubmittable, SubmissionResponse submission) {}

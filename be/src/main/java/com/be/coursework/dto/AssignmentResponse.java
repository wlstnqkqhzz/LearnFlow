package com.be.coursework.dto;
import com.be.coursework.entity.Assignment;
import java.math.BigDecimal;
import java.time.*;
public record AssignmentResponse(Long assignmentId, Long courseId, String title, String description, boolean required,
    LocalDate dueDate, BigDecimal passingScore, int sortOrder, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AssignmentResponse from(Assignment a) {
        return new AssignmentResponse(a.getId(), a.getCourse().getId(), a.getTitle(), a.getDescription(), a.isRequired(),
            a.getDueDate(), a.getPassingScore(), a.getSortOrder(), a.getCreatedAt(), a.getUpdatedAt());
    }
}

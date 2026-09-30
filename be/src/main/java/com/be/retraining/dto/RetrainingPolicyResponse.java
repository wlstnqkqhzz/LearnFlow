package com.be.retraining.dto;

import com.be.retraining.entity.RetrainingPolicy;
import java.time.*;

public record RetrainingPolicyResponse(Long id, Long sourceCourseId, String baseTitle,
        boolean enabled, boolean autoCreate, boolean autoOpen, int intervalMonths, LocalDate firstStartDate,
        int durationDays, int generationLeadDays, int nextOccurrenceNumber, LocalDate nextGenerationDate,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static RetrainingPolicyResponse from(RetrainingPolicy p) {
        return new RetrainingPolicyResponse(p.getId(), p.getSourceCourse().getId(), p.getBaseTitle(),
                p.isEnabled(), p.isAutoCreate(), p.isAutoOpen(), p.getIntervalMonths(), p.getFirstStartDate(),
                p.getDurationDays(), p.getGenerationLeadDays(), p.getNextOccurrenceNumber(), p.getNextGenerationDate(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}

package com.be.exam.dto;

// Read-only projection of the same rules used by start; it does not allocate an attempt.
public record ExamEligibilityResponse(boolean canStart, boolean canContinue, boolean passed,
        int attemptCount, int maxAttempts, int remainingAttempts, Long openAttemptId, String blockedReason) {}

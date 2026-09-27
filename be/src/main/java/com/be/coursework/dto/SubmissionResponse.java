package com.be.coursework.dto;
import com.be.coursework.entity.AssignmentSubmission;
import com.be.coursework.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record SubmissionResponse(Long submissionId, Long assignmentId, Long enrollmentId, Long memberId, String memberName,
    AssignmentSubmissionType submissionType, String content, LocalDateTime submittedAt, int submissionCount,
    AssignmentSubmissionStatus status, BigDecimal score, Boolean passed, String feedback,
    LocalDateTime gradedAt, Long gradedByMemberId, String gradedByName, Long version) {
    public static SubmissionResponse from(AssignmentSubmission s) {
        var member = s.getEnrollment().getMember();
        var grader = s.getGradedBy();
        return new SubmissionResponse(s.getId(), s.getAssignment().getId(), s.getEnrollment().getId(), member.getId(), member.getName(),
            s.getSubmissionType(), s.getContent(), s.getSubmittedAt(), s.getSubmissionCount(), s.status(), s.getScore(),
            s.getPassed(), s.getFeedback(), s.getGradedAt(), grader == null ? null : grader.getId(),
            grader == null ? null : grader.getName(), s.getVersion());
    }
}

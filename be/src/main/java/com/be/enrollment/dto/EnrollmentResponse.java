package com.be.enrollment.dto;

import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.*;
import java.time.*;

// 수동 배정의 assignmentRuleId는 null로 정상 반환
public record EnrollmentResponse(Long enrollmentId, Long memberId, String memberName, Long courseId,
        String courseTitle, EnrollmentStatus status, AssignmentSource assignmentSource, Long assignmentRuleId,
        LocalDateTime assignedAt, LocalDateTime startedAt, LocalDateTime completedAt, LocalDate dueDate) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.getId(), enrollment.getMember().getId(),
                enrollment.getMember().getName(), enrollment.getCourse().getId(), enrollment.getCourse().getTitle(),
                enrollment.getStatus(), enrollment.getAssignmentSource(),
                enrollment.getAssignmentRule() == null ? null : enrollment.getAssignmentRule().getId(),
                enrollment.getAssignedAt(), enrollment.getStartedAt(), enrollment.getCompletedAt(), enrollment.getDueDate());
    }
}

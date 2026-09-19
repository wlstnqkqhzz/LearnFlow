package com.be.course.dto;

import com.be.course.entity.Course;
import com.be.course.enums.*;
import java.math.BigDecimal;
import java.time.*;

// 엔티티 및 강사 개인정보 전체를 노출하지 않는 교육과정 응답
public record CourseResponse(Long id, String title, String description, CourseType courseType,
                             CourseStatus status, LocalDate startDate, LocalDate endDate,
                             BigDecimal passingProgressRate, Long instructorId, String instructorName,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static CourseResponse from(Course course) {
        var instructor = course.getInstructor();
        return new CourseResponse(course.getId(), course.getTitle(), course.getDescription(),
                course.getCourseType(), course.getStatus(), course.getStartDate(), course.getEndDate(),
                course.getPassingProgressRate(), instructor == null ? null : instructor.getId(),
                instructor == null ? null : instructor.getName(), course.getCreatedAt(), course.getUpdatedAt());
    }
}

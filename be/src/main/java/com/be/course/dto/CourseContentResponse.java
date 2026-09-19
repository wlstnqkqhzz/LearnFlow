package com.be.course.dto;

import com.be.course.entity.CourseContent;
import com.be.course.enums.ContentType;
import java.time.LocalDateTime;

// 교육 콘텐츠 응답: 순서는 엔티티의 sortOrder를 그대로 사용
public record CourseContentResponse(Long id, Long courseId, String title, ContentType contentType,
                                    String contentUrl, Integer durationSeconds, int sortOrder, boolean required,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static CourseContentResponse from(CourseContent content) {
        return new CourseContentResponse(content.getId(), content.getCourse().getId(), content.getTitle(),
                content.getContentType(), content.getContentUrl(), content.getDurationSeconds(),
                content.getSortOrder(), content.isRequired(), content.getCreatedAt(), content.getUpdatedAt());
    }
}

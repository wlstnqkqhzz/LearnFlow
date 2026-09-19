package com.be.enrollment.dto;

import com.be.course.entity.CourseContent;
import com.be.course.enums.ContentType;
import com.be.enrollment.entity.ContentProgress;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// 진도 row가 없어도 콘텐츠 목록에 포함하며 0%로 표시
public record ContentProgressResponse(Long contentId, String title, ContentType contentType,
        boolean required, int sortOrder, BigDecimal progressRate, LocalDateTime completedAt) {
    public static ContentProgressResponse from(CourseContent content, ContentProgress progress) {
        return new ContentProgressResponse(content.getId(), content.getTitle(), content.getContentType(),
                content.isRequired(), content.getSortOrder(),
                progress == null ? new BigDecimal("0.00") : progress.getProgressRate(),
                progress == null ? null : progress.getCompletedAt());
    }
}

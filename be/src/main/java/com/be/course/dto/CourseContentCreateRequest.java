package com.be.course.dto;

import com.be.course.enums.ContentType;
import jakarta.validation.constraints.*;

// 콘텐츠 생성 입력: 순서는 명시하며 필수 여부 생략 시 true
public record CourseContentCreateRequest(@NotBlank @Size(max = 200) String title,
        @NotNull ContentType contentType, @NotBlank @Size(max = 2048) String contentUrl,
        @PositiveOrZero Integer durationSeconds, @NotNull @Positive Integer sortOrder, Boolean required) {
    public CourseContentCreateRequest {
        title = title == null ? null : title.trim();
        contentUrl = contentUrl == null ? null : contentUrl.trim();
        required = required == null ? Boolean.TRUE : required;
    }
}

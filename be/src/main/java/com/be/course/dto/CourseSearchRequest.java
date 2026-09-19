package com.be.course.dto;

import com.be.course.enums.*;
import jakarta.validation.constraints.*;

// 페이지는 0부터 시작, ID 오름차순 정렬
public record CourseSearchRequest(@Min(0) Integer page, @Min(1) @Max(100) Integer size,
                                  CourseStatus status, CourseType type, @Positive Long instructorId,
                                  @Size(max = 200) String keyword) {
    public CourseSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}

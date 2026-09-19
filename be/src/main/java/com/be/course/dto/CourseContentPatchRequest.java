package com.be.course.dto;

import com.be.course.enums.ContentType;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import lombok.Getter;

// 콘텐츠 부분 수정: 생략은 유지, durationSeconds만 명시적 null 허용
@Getter
public class CourseContentPatchRequest {
    // 콘텐츠명
    @Size(max = 200) @Pattern(regexp = "(?s).*\\S.*")
    private String title;
    // 콘텐츠 유형
    private ContentType contentType;
    // 콘텐츠 URL
    @Size(max = 2048) @Pattern(regexp = "(?s).*\\S.*")
    private String contentUrl;
    // 재생 길이 (초)
    @PositiveOrZero
    private Integer durationSeconds;
    @JsonIgnore
    private boolean durationSecondsPresent;
    // 변경할 순서 (사용 중인 순서는 충돌 처리)
    @Positive
    private Integer sortOrder;
    // 필수 콘텐츠 여부
    private Boolean required;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTitle(String title) {
        this.title = title.trim();
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl.trim();
    }

    @JsonSetter
    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
        this.durationSecondsPresent = true;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setRequired(Boolean required) {
        this.required = required;
    }
}

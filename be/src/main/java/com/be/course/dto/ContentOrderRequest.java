package com.be.course.dto;

import jakarta.validation.constraints.*;
import java.util.List;

// 현재 교육과정의 전체 콘텐츠 ID를 원하는 순서대로 전달 (빈 과정은 빈 목록 허용)
public record ContentOrderRequest(@NotNull List<@NotNull @Positive Long> contentIds) {}

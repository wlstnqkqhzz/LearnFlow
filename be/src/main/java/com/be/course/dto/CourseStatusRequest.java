package com.be.course.dto;

import com.be.course.enums.CourseStatus;
import jakarta.validation.constraints.NotNull;

// 일반 정보 수정과 분리된 상태 변경 입력
public record CourseStatusRequest(@NotNull CourseStatus status) {}

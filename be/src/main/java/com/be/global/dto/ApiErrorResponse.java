package com.be.global.dto;

import java.util.List;

// 모든 API 오류의 공통 응답 - 거부된 원문 입력값은 포함하지 않음
public record ApiErrorResponse(String code, String message, List<FieldError> errors) {
    public record FieldError(String field, String message) {
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, List.of());
    }
}

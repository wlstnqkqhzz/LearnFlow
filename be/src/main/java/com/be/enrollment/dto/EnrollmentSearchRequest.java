package com.be.enrollment.dto;

import com.be.enrollment.enums.EnrollmentStatus;
import jakarta.validation.constraints.*;

// 회원 ID는 요청으로 받지 않음: 본인 조회는 인증 주체로 고정
public record EnrollmentSearchRequest(@Min(0) Integer page, @Min(1) @Max(100) Integer size,
                                      EnrollmentStatus status) {
    public EnrollmentSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}

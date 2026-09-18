package com.be.global.dto;

import java.util.List;
import org.springframework.data.domain.Page;

// Spring 내부 Page 직렬화 형식에 의존하지 않는 목록 응답
public record PageResponse<T>(List<T> content, int page, int size,
                              long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> result) {
        return new PageResponse<>(List.copyOf(result.getContent()), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}

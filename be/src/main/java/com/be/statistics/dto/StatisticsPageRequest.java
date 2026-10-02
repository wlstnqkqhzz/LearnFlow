package com.be.statistics.dto;

import jakarta.validation.constraints.*;

// Stable ID order; arbitrary client SQL/sort expressions are not accepted.
public record StatisticsPageRequest(@Min(0) Integer page, @Min(1) @Max(100) Integer size) {
    public StatisticsPageRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}

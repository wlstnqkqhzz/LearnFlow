package com.be.notification.dto;

import jakarta.validation.constraints.*;

public record NotificationSearchRequest(@Min(0) Integer page, @Min(1) @Max(100) Integer size) {
    public NotificationSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}

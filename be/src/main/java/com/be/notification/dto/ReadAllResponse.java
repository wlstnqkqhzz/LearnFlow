package com.be.notification.dto;

import java.time.LocalDateTime;

// 재조회 실패 시에도 클라이언트가 실제 읽음 처리 시각을 반영할 수 있다.
public record ReadAllResponse(LocalDateTime readAt) {}

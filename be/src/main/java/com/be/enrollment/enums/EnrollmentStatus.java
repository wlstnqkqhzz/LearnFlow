package com.be.enrollment.enums;

// 교육 수강 상태 Enum

public enum EnrollmentStatus {
    ASSIGNED,               // 교육 배정 완료 - 학습 시작 전
    IN_PROGRESS,            // 학습 또는 시험 응시 진행 중
    COMPLETED,              // 수료 조건 충족 - MVP 최종 상태
    FAILED,                 // 최대 응시 횟수 소진 후 불합격 - MVP 최종 상태
    EXPIRED                 // 마감일까지 미수료 - MVP 최종 상태
}
package com.be.course.enums;

// 교육과정 운영 상태 Enum

public enum CourseStatus {
    DRAFT,              // 작성 중 - 교육 구성 편집 가능
    OPEN,               // 공개 - 교육 배정 및 기간 내 학습 가능
    CLOSED              // 종료 - MVP 최종 상태
}
package com.be.exam.enums;

// 시험 문제 유형 Enum

public enum QuestionType {
    // 단일 선택형
    SINGLE_CHOICE,
    // 복수 선택형 - 정답 집합과 정확히 일치해야 정답
    MULTIPLE_CHOICE,
    // 참 / 거짓 선택형
    TRUE_FALSE
}

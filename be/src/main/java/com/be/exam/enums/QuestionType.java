package com.be.exam.enums;

// 시험 문제 유형 Enum

public enum QuestionType {
    SINGLE_CHOICE,          // 단일 선택형
    MULTIPLE_CHOICE,        // 복수 선택형 - 정답 집합과 정확히 일치해야 정답
    TRUE_FALSE              // 참 OR 거짓 선택형
}
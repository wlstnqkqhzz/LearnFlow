package com.be.member.enums;

// 회원 재직 상태 Enum

public enum MemberStatus {
    ACTIVE,             // 재직 중 - 신규 자동 배정 대상
    ON_LEAVE,           // 휴직 중 - 로그인 가능, 신규 자동 배정 제외
    RESIGNED            // 퇴사 - 로그인 및 신규 자동 배정 제외
}
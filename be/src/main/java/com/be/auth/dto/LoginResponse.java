package com.be.auth.dto;

// 로그인과 재발급에서 공통으로 사용하는 토큰 및 유효 기간(초) 응답
public record LoginResponse(String accessToken, String refreshToken, String tokenType, long accessTokenExpiresInSeconds, long refreshTokenExpiresInSeconds) {
    @Override
    public String toString() {
        return "LoginResponse[accessToken=REDACTED, refreshToken=REDACTED, tokenType=" + tokenType + ", accessTokenExpiresInSeconds=" + accessTokenExpiresInSeconds + ", refreshTokenExpiresInSeconds=" + refreshTokenExpiresInSeconds + "]";
    }
}

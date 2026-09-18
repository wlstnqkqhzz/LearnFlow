package com.be.auth.dto;

// Access Token과 유효 기간(초)만 반환
public record LoginResponse(String accessToken, String refreshToken, String tokenType, long accessTokenExpiresInSeconds, long refreshTokenExpiresInSeconds) {
    @Override
    public String toString() {
        return "LoginResponse[accessToken=REDACTED, tokenType=" + tokenType + ", accessTokenExpiresInSeconds=" + accessTokenExpiresInSeconds + ", refreshTokenExpiresInSeconds=" + refreshTokenExpiresInSeconds + "]";
    }
}

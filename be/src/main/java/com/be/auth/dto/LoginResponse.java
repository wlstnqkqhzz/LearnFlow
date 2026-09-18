package com.be.auth.dto;

// Access Token과 유효 기간(초)만 반환
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() {
        return "LoginResponse[accessToken=REDACTED, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}

package com.be.global.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 필수 환경 설정 - Secret은 Base64 인코딩된 32바이트 이상의 무작위 키
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(@NotBlank String secret, @Min(1) long accessTokenTtlSeconds, @Min(1) long refreshTokenTtlSeconds) {
    @Override
    public String toString() {
        return "JwtProperties[secret=REDACTED, accessTokenTtlSeconds=" + accessTokenTtlSeconds
                + ", refreshTokenTtlSeconds=" + refreshTokenTtlSeconds + "]";
    }
}

package com.be.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Redis를 이용해 회원별 Refresh Token을 저장·조회·검증·삭제하는 인증 전용 서비스
// Access Token 재발급 및 로그아웃 시 Refresh Token 상태를 관리한다.
@Service
public class RefreshTokenService {
    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Refresh Token 저장
    public void save(Long memberId, String refreshToken, long ttlSeconds) {
        redisTemplate.opsForValue().set(
                key(memberId),
                refreshToken,
                Duration.ofSeconds(ttlSeconds)
        );
    }

    // Refresh Token 조회
    public String find(Long memberId) {
        return redisTemplate.opsForValue().get(key(memberId));
    }

    // Refresh Token 삭제
    public void delete(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    // Redis에 저장된 Refresh Token과 일치하는지 확인
    public boolean matches(Long memberId, String refreshToken) {
        String savedToken = find(memberId);
        return savedToken != null && savedToken.equals(refreshToken);
    }

    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}

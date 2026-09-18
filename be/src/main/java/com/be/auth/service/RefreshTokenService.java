package com.be.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

// Redis를 이용해 회원별 Refresh Token을 저장·조회·검증·삭제하는 인증 전용 서비스
// Access Token 재발급 및 로그아웃 시 Refresh Token 상태를 관리한다.
@Service
public class RefreshTokenService {
    private static final String KEY_PREFIX = "auth:refresh:";
    // 비교와 TTL 포함 덮어쓰기를 한 번에 실행하여 재사용 및 로그아웃 후 복원 방지
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
            """, Long.class);

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

    // 저장된 값이 요청 토큰과 여전히 일치할 때에만 새 토큰과 TTL로 교체
    public boolean rotate(Long memberId, String expectedToken, String newToken, long ttlSeconds) {
        Long result = redisTemplate.execute(ROTATE_SCRIPT, List.of(key(memberId)),
                expectedToken, newToken, Long.toString(ttlSeconds));
        return Long.valueOf(1).equals(result);
    }
}

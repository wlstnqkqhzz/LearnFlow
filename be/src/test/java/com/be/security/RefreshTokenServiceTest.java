package com.be.security;

import com.be.auth.service.RefreshTokenService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Redis 키 및 TTL 전달 계약 검증 (실제 서버 실행은 별도 통합 테스트)
class RefreshTokenServiceTest {
    final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    final ValueOperations<String, String> values = mock(ValueOperations.class);
    final RefreshTokenService service = new RefreshTokenService(redis);

    @Test
    void savesWithTtlAndFindsMatchesAndDeletesOnlyMemberKey() {
        when(redis.opsForValue()).thenReturn(values);
        service.save(7L, "refresh-a", 3600);
        verify(values).set("auth:refresh:7", "refresh-a", Duration.ofSeconds(3600));
        when(values.get("auth:refresh:7")).thenReturn("refresh-a");
        assertThat(service.find(7L)).isEqualTo("refresh-a");
        assertThat(service.matches(7L, "refresh-a")).isTrue();
        assertThat(service.matches(7L, "refresh-b")).isFalse();
        assertThat(service.matches(8L, "refresh-a")).isFalse();
        service.delete(7L);
        verify(redis).delete("auth:refresh:7");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rotationPassesExpectedTokenNewValueAndFreshTtlToSingleScript() {
        when(redis.execute(any(RedisScript.class), eq(List.of("auth:refresh:7")),
                eq("refresh-a"), eq("refresh-b"), eq("3600"))).thenReturn(1L);
        assertThat(service.rotate(7L, "refresh-a", "refresh-b", 3600)).isTrue();
        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), eq(List.of("auth:refresh:7")),
                eq("refresh-a"), eq("refresh-b"), eq("3600"));
        assertThat(script.getValue().getResultType()).isEqualTo(Long.class);
        verify(redis, never()).delete(anyString());
        verify(redis, never()).opsForValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedComparisonOrMissingResultDoesNotSucceed() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(0L).thenReturn(null);
        assertThat(service.rotate(7L, "old", "new", 3600)).isFalse();
        assertThat(service.rotate(7L, "old", "new", 3600)).isFalse();
    }
}

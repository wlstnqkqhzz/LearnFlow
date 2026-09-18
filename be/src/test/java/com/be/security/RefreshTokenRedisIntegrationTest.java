package com.be.security;

import com.be.auth.service.RefreshTokenService;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.*;

// 명시적으로 활성화할 때만 로컬 테스트 Redis 사용; 양수 회원 ID와 겹치지 않는 임시 키만 생성
@EnabledIfSystemProperty(named = "redis.integration-test", matches = "true")
class RefreshTokenRedisIntegrationTest {
    LettuceConnectionFactory connection;
    StringRedisTemplate redis;
    RefreshTokenService service;
    long memberId;
    boolean ownsKey;

    @BeforeEach
    void setUp() {
        var configuration = new RedisStandaloneConfiguration(
                System.getProperty("redis.test.host", "localhost"),
                Integer.getInteger("redis.test.port", 6379));
        var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(3)).build();
        connection = new LettuceConnectionFactory(configuration, client);
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        service = new RefreshTokenService(redis);
        // 실제 회원 키를 건드리지 않으며, 중단되어도 짧은 TTL로 자동 정리
        do {
            memberId = -ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
            ownsKey = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(), "old", Duration.ofSeconds(10)));
        } while (!ownsKey);
    }

    @AfterEach
    void cleanUp() {
        try {
            if (ownsKey) {
                service.delete(memberId);
            }
        } finally {
            if (connection != null) {
                connection.destroy();
            }
        }
    }

    @Test
    void actualScriptReplacesValueResetsTtlAndRejectsReplay() {
        assertThat(service.rotate(memberId, "old", "new", 60)).isTrue();
        assertThat(service.find(memberId)).isEqualTo("new");
        assertThat(redis.getExpire(key(), TimeUnit.SECONDS)).isBetween(55L, 60L);
        assertThat(service.rotate(memberId, "old", "replay", 60)).isFalse();
        assertThat(service.find(memberId)).isEqualTo("new");
    }

    @Test
    void concurrentRotationHasExactlyOneWinner() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return service.rotate(memberId, "old", "first", 60);
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return service.rotate(memberId, "old", "second", 60);
            });
            start.countDown();
            boolean firstWon = first.get(5, TimeUnit.SECONDS);
            boolean secondWon = second.get(5, TimeUnit.SECONDS);
            assertThat(firstWon ^ secondWon).isTrue();
            assertThat(service.find(memberId)).isEqualTo(firstWon ? "first" : "second");
        }
    }

    @Test
    void logoutBetweenReadAndRotationCannotResurrectToken() {
        assertThat(service.matches(memberId, "old")).isTrue();
        service.delete(memberId);
        assertThat(service.rotate(memberId, "old", "new", 60)).isFalse();
        assertThat(service.find(memberId)).isNull();
    }

    private String key() {
        return "auth:refresh:" + memberId;
    }
}

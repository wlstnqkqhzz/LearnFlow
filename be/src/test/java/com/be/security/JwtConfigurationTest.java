package com.be.security;

import com.be.global.security.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;

import static org.assertj.core.api.Assertions.*;

// Secret과 만료 시간의 필수 설정 및 기동 시 검증
class JwtConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurationForTest.class);

    @Test
    void startsWithValidSettings() {
        runner.withPropertyValues("jwt.secret=" + JwtTestSupport.secret(),
                "jwt.access-token-ttl-seconds=300")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtTokenProvider.class));
    }

    @Test
    void refusesMissingSecret() {
        runner.withPropertyValues("jwt.access-token-ttl-seconds=300")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesMissingOrNonPositiveExpiration() {
        runner.withPropertyValues("jwt.secret=" + JwtTestSupport.secret())
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("jwt.secret=" + JwtTestSupport.secret(),
                "jwt.access-token-ttl-seconds=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesWeakSecret() {
        runner.withPropertyValues("jwt.secret=c2hvcnQ=", "jwt.access-token-ttl-seconds=300")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class ConfigurationForTest {
        @Bean
        JwtTokenProvider tokens(JwtProperties properties) {
            return new JwtTokenProvider(properties, Clock.systemUTC());
        }
    }
}

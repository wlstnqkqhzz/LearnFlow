package com.be.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

// 회원 기능에 필요한 비밀번호 해시와 UTC 시계만 제공
@Configuration(proxyBeanMethods = false)
public class MemberSupportConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
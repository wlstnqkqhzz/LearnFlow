package com.be.global.config;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// JWT 도입 전 로컬 API 확인용 설정 - local-api 프로필에서만 활성화
@Configuration(proxyBeanMethods = false)
@Profile("local-api")
public class LocalApiSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain localApiSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/departments", "/api/departments/**",
                        "/api/job-positions", "/api/job-positions/**", "/api/members", "/api/members/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    // API 이외 경로가 무인증으로 열리지 않도록 기본 차단
    @Bean
    @Order(2)
    SecurityFilterChain otherRequests(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll()).build();
    }
}

package com.be.global.config;

import com.be.global.security.*;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

// 프로필과 관계없이 적용되는 Access Token 전용 API 보안 설정
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    @Bean
    JwtTokenProvider jwtTokenProvider(JwtProperties properties, Clock clock) {
        return new JwtTokenProvider(properties, clock);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokens,
                                           MemberAuthenticationService members, ObjectMapper objectMapper)
            throws Exception {
        var entryPoint = new JsonAuthenticationEntryPoint(objectMapper);
        var deniedHandler = new JsonAccessDeniedHandler(objectMapper);
        var filter = new JwtAuthenticationFilter(tokens, members, entryPoint);
        // Filter는 체인 안에서만 생성하여 서블릿 필터로 중복 등록하지 않음
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        // 관리 하위 경로는 기존 Course GET(INSTRUCTOR 허용)보다 먼저 제한
                        .requestMatchers("/api/courses/*/assignment-rules", "/api/courses/*/assignment-rules/**",
                                "/api/courses/*/enrollments", "/api/courses/*/enrollments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/enrollments/me").hasRole("EMPLOYEE")
                        .requestMatchers("/api/enrollments", "/api/enrollments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/**")
                        .hasAnyRole("ADMIN", "INSTRUCTOR")
                        .requestMatchers("/api/courses", "/api/courses/**").hasRole("ADMIN")
                        .requestMatchers("/api/departments", "/api/departments/**",
                                "/api/job-positions", "/api/job-positions/**", "/api/members", "/api/members/**")
                        .hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

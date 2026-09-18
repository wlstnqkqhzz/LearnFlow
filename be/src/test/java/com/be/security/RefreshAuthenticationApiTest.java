package com.be.security;

import com.be.auth.controller.AuthController;
import com.be.auth.dto.*;
import com.be.auth.service.*;
import com.be.global.config.*;
import com.be.global.exception.GlobalExceptionHandler;
import com.be.global.security.*;
import com.be.member.entity.Member;
import com.be.member.enums.*;
import com.be.member.repository.MemberRepository;
import com.be.organization.entity.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 JWT·인증 서비스·보안 체인 통합 검증 (회원 저장소 및 Redis 서비스만 대체)
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, MemberSupportConfig.class, AuthService.class,
        MemberAuthenticationService.class, GlobalExceptionHandler.class})
class RefreshAuthenticationApiTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper mapper;
    @MockitoBean MemberRepository repository;
    @MockitoBean RefreshTokenService refreshTokens;
    final Map<Long, String> stored = new ConcurrentHashMap<>();
    Member member;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds", () -> 300);
        registry.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @BeforeEach
    void setUp() {
        stored.clear();
        member = Member.create("E001", "old@example.com", "unused-hash", "직원",
                Department.create("DEV", "개발", null), JobPosition.create("DEV", "개발자"),
                LocalDate.of(2026, 9, 18));
        ReflectionTestUtils.setField(member, "id", 1L);
        when(repository.findWithRolesById(1L)).thenReturn(Optional.of(member));
        when(refreshTokens.matches(anyLong(), anyString())).thenAnswer(call ->
                Objects.equals(stored.get(call.getArgument(0)), call.getArgument(1)));
        when(refreshTokens.rotate(anyLong(), anyString(), anyString(), anyLong())).thenAnswer(call ->
                stored.replace(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        doAnswer(call -> { stored.remove(call.getArgument(0)); return null; })
                .when(refreshTokens).delete(anyLong());
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"ACTIVE", "ON_LEAVE"})
    void rotatesAndRejectsReplayUsingLatestMember(MemberStatus status) throws Exception {
        String original = savedToken();
        member.updateProfile("latest@example.com", "직원", member.getHireDate());
        member.addRole(Role.ADMIN);
        if (status == MemberStatus.ON_LEAVE) {
            member.changeStatus(status, LocalDateTime.now(ZoneOffset.UTC));
        }
        var response = refresh(original).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessTokenExpiresInSeconds").value(300))
                .andExpect(jsonPath("$.refreshTokenExpiresInSeconds").value(3600)).andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsString());
        String replacement = json.get("refreshToken").asString();
        assertThat(replacement).isNotEqualTo(original).isEqualTo(stored.get(1L));
        assertThat(tokens.parseRefreshToken(replacement)).isEqualTo(1L);
        var principal = tokens.parseAccessToken(json.get("accessToken").asString());
        assertThat(principal.email()).isEqualTo("latest@example.com");
        assertThat(principal.roles()).contains(Role.ADMIN, Role.EMPLOYEE);
        verify(refreshTokens).rotate(1L, original, replacement, 3600);
        refresh(original).andExpect(status().isUnauthorized());
        assertThat(stored.get(1L)).isEqualTo(replacement);
        refresh(replacement).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "expired", "access", "missing", "mismatch", "resigned", "deleted"})
    void authenticationFailuresHaveSameContract(String reason) throws Exception {
        String token = savedToken();
        switch (reason) {
            case "malformed" -> token = "not-a-jwt";
            case "expired" -> token = new JwtTokenProvider(new JwtProperties(JwtTestSupport.secret(), 1, 1),
                    Clock.fixed(Instant.now().minusSeconds(60), ZoneOffset.UTC)).createRefreshToken(1L);
            case "access" -> token = tokens.createAccessToken(MemberPrincipal.from(member));
            case "missing" -> stored.clear();
            case "mismatch" -> stored.put(1L, tokens.createRefreshToken(1L));
            case "resigned" -> member.changeStatus(MemberStatus.RESIGNED, LocalDateTime.now(ZoneOffset.UTC));
            case "deleted" -> when(repository.findWithRolesById(1L)).thenReturn(Optional.empty());
        }
        refresh(token).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요하거나 인증 정보가 유효하지 않습니다."))
                .andExpect(jsonPath("$.errors").isEmpty());
        verify(refreshTokens, never()).rotate(anyLong(), anyString(), anyString(), anyLong());
        verify(refreshTokens, never()).delete(anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"refreshToken\":null}", "{\"refreshToken\":\" \"}"})
    void missingRefreshTokenIsValidationError(String body) throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(refreshTokens, repository);
    }

    @Test
    void refreshIgnoresExpiredAuthorizationHeader() throws Exception {
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RefreshTokenRequest(savedToken()))))
                .andExpect(status().isOk());
    }

    @Test
    void lostRotationRaceReturns401WithoutSavingOrDeleting() throws Exception {
        String token = savedToken();
        when(refreshTokens.rotate(anyLong(), anyString(), anyString(), anyLong())).thenReturn(false);
        refresh(token).andExpect(status().isUnauthorized());
        verify(refreshTokens, never()).save(anyLong(), anyString(), anyLong());
        verify(refreshTokens, never()).delete(anyLong());
    }

    @Test
    void employeeLogoutDeletesRefreshButDoesNotBlacklistAccess() throws Exception {
        String refresh = savedToken();
        String access = tokens.createAccessToken(MemberPrincipal.from(member));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(refreshTokens).delete(1L);
        assertThat(stored).isEmpty();
        refresh(refresh).andExpect(status().isUnauthorized());
        // 로그아웃은 멱등적이며 기존 Access Token 자체는 만료 전까지 유효
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutRequiresValidAccessToken() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + savedToken()))
                .andExpect(status().isUnauthorized());
        verify(refreshTokens, never()).delete(anyLong());
    }

    @Test
    void sensitiveDtosRedactBothTokens() {
        assertThat(new RefreshTokenRequest("secret-refresh").toString()).doesNotContain("secret-refresh");
        assertThat(new LoginResponse("secret-access", "secret-refresh", "Bearer", 300, 3600).toString())
                .contains("accessToken=REDACTED", "refreshToken=REDACTED")
                .doesNotContain("secret-access", "secret-refresh");
    }

    private String savedToken() {
        String token = tokens.createRefreshToken(1L);
        stored.put(1L, token);
        return token;
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RefreshTokenRequest(token))));
    }
}

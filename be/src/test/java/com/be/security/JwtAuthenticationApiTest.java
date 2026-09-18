package com.be.security;

import com.be.auth.controller.AuthController;
import com.be.auth.service.AuthService;
import com.be.global.config.*;
import com.be.global.security.*;
import com.be.global.exception.GlobalExceptionHandler;
import com.be.member.controller.MemberController;
import com.be.member.dto.MemberResponse;
import com.be.member.entity.Member;
import com.be.member.enums.*;
import com.be.member.repository.MemberRepository;
import com.be.member.service.MemberService;
import com.be.organization.controller.*;
import com.be.organization.entity.*;
import com.be.organization.service.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 로그인 Service·JWT·Security 필터를 함께 검증 (DB 및 Redis 저장 서비스는 Mock)
// 과거 local-api 프로필을 켜도 무인증 관리 API 접근이 허용되지 않아야 함
@WebMvcTest({AuthController.class, MemberController.class, DepartmentController.class, JobPositionController.class})
@Import({SecurityConfig.class, MemberSupportConfig.class, AuthService.class,
        MemberAuthenticationService.class, GlobalExceptionHandler.class})
@ActiveProfiles("local-api")
class JwtAuthenticationApiTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper mapper;
    @MockitoBean MemberRepository repository;
    @MockitoBean com.be.auth.service.RefreshTokenService refreshTokens;
    @MockitoBean MemberService members;
    @MockitoBean DepartmentService departments;
    @MockitoBean JobPositionService positions;
    Member member;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds", () -> 300);
        registry.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @BeforeEach
    void setUp() {
        member = Member.create("E001", "user@example.com", encoder.encode("password123!"), "직원",
                Department.create("DEV", "개발", null), JobPosition.create("DEV", "개발자"),
                LocalDate.of(2026, 9, 18));
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"ACTIVE", "ON_LEAVE"})
    void loginAllowsActiveAndLeave(MemberStatus status) throws Exception {
        if (status == MemberStatus.ON_LEAVE) {
            member.changeStatus(status, LocalDateTime.now(ZoneOffset.UTC));
        }
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(member));
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" USER@Example.COM \",\"password\":\"password123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessTokenExpiresInSeconds").value(300))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshToken").isString()).andReturn();
        String token = mapper.readTree(response.getResponse().getContentAsString()).get("accessToken").asString();
        assertThat(tokens.parseAccessToken(token).memberId()).isEqualTo(1L);
        String refreshToken = mapper.readTree(response.getResponse().getContentAsString()).get("refreshToken").asString();
        assertThat(tokens.parseRefreshToken(refreshToken)).isEqualTo(1L);
        verify(refreshTokens).save(1L, refreshToken, 3600);
        assertThat(response.getRequest().getSession(false)).isNull();
        assertThat(response.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong-password", "resigned"})
    void rejectsInvalidLoginWithSameResponse(String reason) throws Exception {
        if (!reason.equals("missing")) {
            when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(member));
        }
        if (reason.equals("resigned")) {
            member.changeStatus(MemberStatus.RESIGNED, LocalDateTime.now(ZoneOffset.UTC));
        }
        String password = reason.equals("wrong-password") ? "incorrect" : "password123!";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.errors").isArray()).andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void rejectsInvalidLoginBody() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/members/1", "/api/departments", "/api/job-positions"})
    void missingTokenReturns401(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer malformed", "Bearer ", "Basic abc", "Token abc"})
    void badHeaderReturns401(String header) throws Exception {
        mvc.perform(get("/api/members/1").header("Authorization", header))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(members);
    }

    @Test
    void duplicateAuthorizationHeadersAreRejected() throws Exception {
        mvc.perform(get("/api/members/1").header("Authorization", "Bearer x", "Bearer y"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/members/1", "/api/departments", "/api/job-positions"})
    void employeeAndInstructorCannotManage(String path) throws Exception {
        member.addRole(Role.INSTRUCTOR);
        String token = tokens.createAccessToken(MemberPrincipal.from(member));
        when(repository.findWithRolesById(1L)).thenReturn(Optional.of(member));
        mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(members, departments, positions);
    }

    @Test
    void adminCanManageWithBearerToken() throws Exception {
        member.addRole(Role.ADMIN);
        when(repository.findWithRolesById(1L)).thenReturn(Optional.of(member));
        when(members.get(1L)).thenReturn(MemberResponse.from(member));
        String token = tokens.createAccessToken(MemberPrincipal.from(member));
        mvc.perform(get("/api/members/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void removedAdminRoleIsEffectiveForExistingToken() throws Exception {
        member.addRole(Role.ADMIN);
        String token = tokens.createAccessToken(MemberPrincipal.from(member));
        member.removeRole(Role.ADMIN);
        when(repository.findWithRolesById(1L)).thenReturn(Optional.of(member));
        mvc.perform(get("/api/members/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void resignationInvalidatesExistingAccess() throws Exception {
        member.addRole(Role.ADMIN);
        String token = tokens.createAccessToken(MemberPrincipal.from(member));
        member.changeStatus(MemberStatus.RESIGNED, LocalDateTime.now(ZoneOffset.UTC));
        when(repository.findWithRolesById(1L)).thenReturn(Optional.of(member));
        mvc.perform(get("/api/members/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedMemberCannotAuthenticate() throws Exception {
        String token = tokens.createAccessToken(MemberPrincipal.from(member));
        mvc.perform(get("/api/members/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        var oldProvider = new JwtTokenProvider(new JwtProperties(JwtTestSupport.secret(), 1, 3600),
                Clock.fixed(Instant.now().minusSeconds(60), ZoneOffset.UTC));
        String token = oldProvider.createAccessToken(MemberPrincipal.from(member));
        mvc.perform(get("/api/members/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginCanIgnoreStaleBearerHeader() throws Exception {
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(member));
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void noSessionAuthenticationCarriesOverBetweenRequests() throws Exception {
        member.addRole(Role.ADMIN);
        when(repository.findWithRolesById(1L)).thenReturn(Optional.of(member));
        when(members.get(1L)).thenReturn(MemberResponse.from(member));
        var response = mvc.perform(get("/api/members/1").header("Authorization",
                        "Bearer " + tokens.createAccessToken(MemberPrincipal.from(member))))
                .andExpect(status().isOk()).andReturn();
        assertThat(response.getRequest().getSession(false)).isNull();
        mvc.perform(get("/api/members/1")).andExpect(status().isUnauthorized());
    }
}

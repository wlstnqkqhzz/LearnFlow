package com.be.mobilepush;

import com.be.global.config.SecurityConfig;
import com.be.global.exception.*;
import com.be.global.security.*;
import com.be.member.enums.Role;
import com.be.security.JwtTestSupport;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static com.be.mobilepush.MobilePushTestSupport.*;

@WebMvcTest(MobilePushController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MobilePushApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean MobilePushSubscriptionService service;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService authenticationService;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("jwt.secret", JwtTestSupport::secret); r.add("jwt.access-token-ttl-seconds", () -> 300); r.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }
    RequestPostProcessor as(Role... roles) {
        var p = new MemberPrincipal(2L, "test@example.com", Set.of(roles));
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }
    @Test void bindingUsesPrincipalAndSafeResponse() throws Exception {
        when(service.bind(eq(2L), eq(SECRET), any())).thenReturn(new MobilePushDtos.Response(1L, MobilePushSubscription.Platform.ANDROID, false, 0, Instant.EPOCH));
        String body = mvc.perform(put("/api/mobile/push/subscriptions/binding").with(as(Role.EMPLOYEE)).header("X-Installation-Secret", SECRET)
                .contentType("application/json").content("{\"installationId\":\"11111111-1111-1111-1111-111111111111\",\"platform\":\"ANDROID\",\"version\":0,\"memberId\":999}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.subscriptionId").value(1)).andReturn().getResponse().getContentAsString();
        verify(service).bind(eq(2L), eq(SECRET), any()); assertThat(body).doesNotContain(SECRET, "token", "secret", "memberId", "installationId");
    }
    @Test void registerGetAndDisableForEmployeeWithAdditionalRole() throws Exception {
        when(service.register(eq(2L), eq(1L), eq(SECRET), any())).thenReturn(new MobilePushDtos.Response(1L, MobilePushSubscription.Platform.IOS, true, 1, Instant.EPOCH));
        mvc.perform(put("/api/mobile/push/subscriptions/1").with(as(Role.EMPLOYEE, Role.ADMIN)).header("X-Installation-Secret", SECRET).contentType("application/json")
                .content("{\"expoPushToken\":\"ExpoPushToken[private]\",\"platform\":\"IOS\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expoPushToken").doesNotExist());
        mvc.perform(get("/api/mobile/push/subscriptions/1").with(as(Role.EMPLOYEE))).andExpect(status().isOk());
        mvc.perform(delete("/api/mobile/push/subscriptions/1").param("version", "1").header("X-Installation-Secret", SECRET).with(as(Role.EMPLOYEE))).andExpect(status().isNoContent());
        verify(service).disable(2L, 1L, SECRET, 1);
    }
    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "INSTRUCTOR"})
    void nonEmployeesForbiddenOnAllRoutes(Role role) throws Exception {
        mvc.perform(get("/api/mobile/push/subscriptions/1").with(as(role))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/mobile/push/subscriptions/1").with(as(role))).andExpect(status().isForbidden());
        mvc.perform(put("/api/mobile/push/subscriptions/1").with(as(role))).andExpect(status().isForbidden());
        mvc.perform(put("/api/mobile/push/subscriptions/binding").with(as(role))).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void anonymousUnauthorizedAndForeignSubscriptionNotFound() throws Exception {
        mvc.perform(get("/api/mobile/push/subscriptions/1")).andExpect(status().isUnauthorized());
        when(service.get(2L, 1L)).thenThrow(new BusinessException(ErrorCode.MOBILE_PUSH_NOT_FOUND));
        mvc.perform(get("/api/mobile/push/subscriptions/1").with(as(Role.EMPLOYEE))).andExpect(status().isNotFound());
    }
    @Test void duplicateTokenAndStaleVersionAre409() throws Exception {
        when(service.register(any(), any(), any(), any())).thenThrow(new BusinessException(ErrorCode.MOBILE_PUSH_CONFLICT));
        mvc.perform(put("/api/mobile/push/subscriptions/1").with(as(Role.EMPLOYEE)).header("X-Installation-Secret", SECRET).contentType("application/json")
                .content("{\"expoPushToken\":\"ExpoPushToken[private]\",\"platform\":\"IOS\",\"version\":0}"))
                .andExpect(status().isConflict());
    }
    @Test void invalidTokenDoesNotEchoRejectedValue() throws Exception {
        String body = mvc.perform(put("/api/mobile/push/subscriptions/1").with(as(Role.EMPLOYEE)).header("X-Installation-Secret", SECRET).contentType("application/json")
                .content("{\"expoPushToken\":\"SECRET-INVALID\",\"platform\":\"IOS\",\"version\":0}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SECRET-INVALID", SECRET); verifyNoInteractions(service);
    }
}

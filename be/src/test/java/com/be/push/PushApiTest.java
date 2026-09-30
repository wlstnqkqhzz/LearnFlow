package com.be.push;

import com.be.assignment.AssignmentFixtures;
import com.be.global.config.*;
import com.be.global.exception.GlobalExceptionHandler;
import com.be.global.security.*;
import com.be.member.enums.Role;
import com.be.member.repository.MemberRepository;
import com.be.security.JwtTestSupport;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static com.be.push.PushTestSupport.*;

@WebMvcTest(PushController.class)
@Import({SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class,
        PushSubscriptionService.class, PushEndpointPolicy.class, PushApiTest.Config.class})
class PushApiTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false) static class Config {
        @Bean PushProperties pushProperties() { return ENABLED; }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean PushSubscriptionRepository subscriptions;
    @MockitoBean MemberRepository members;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService authenticationService;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("jwt.secret", JwtTestSupport::secret);
        r.add("jwt.access-token-ttl-seconds", () -> 300); r.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }
    @BeforeEach void setup() {
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(AssignmentFixtures.member(2L)));
        when(subscriptions.saveAndFlush(any())).thenAnswer(c -> { PushSubscription s = c.getArgument(0); AssignmentFixtures.id(s, 8L); return s; });
    }
    @Test void configOnlyExposesPublicKey() throws Exception {
        String json = mvc.perform(get("/api/push/config").with(as(2L))).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.publicKey").value("public-test"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("private", "subject");
        var disabled = new PushController(null, new PushProperties(false, "hidden", "secret", null)).config();
        assertThat(disabled.getBody().publicKey()).isNull();
    }
    @Test void registrationIgnoresSpoofedMemberAndNeverReturnsCredentials() throws Exception {
        var request = request("private-endpoint");
        String json = mapper.writeValueAsString(request);
        json = json.substring(0, json.length() - 1) + ",\"memberId\":999}";
        String body = mvc.perform(put("/api/push/subscriptions").with(as(2L)).contentType("application/json").content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subscriptionId").value(8)).andReturn().getResponse().getContentAsString();
        verify(members).findByIdForUpdate(2L); verify(members, never()).findByIdForUpdate(999L);
        assertThat(body).doesNotContain("endpoint", "p256dh", "auth", request.p256dh(), AUTH);
    }
    @Test void ownGetAndRepeatedDelete() throws Exception {
        var s = PushSubscription.create(AssignmentFixtures.member(2L), "hash", request("own"), CLOCK.instant());
        AssignmentFixtures.id(s, 8L);
        when(subscriptions.findByIdAndMemberId(8L, 2L)).thenReturn(Optional.of(s));
        mvc.perform(get("/api/push/subscriptions/8").with(as(2L))).andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").doesNotExist()).andExpect(jsonPath("$.p256dh").doesNotExist()).andExpect(jsonPath("$.auth").doesNotExist());
        for (int i = 0; i < 2; i++) mvc.perform(delete("/api/push/subscriptions/8").with(as(2L))).andExpect(status().isNoContent());
        assertThat(s.isEnabled()).isFalse();
    }
    @Test void foreignGetAndDeleteAre404EvenForAdmin() throws Exception {
        mvc.perform(get("/api/push/subscriptions/8").with(as(3L))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/push/subscriptions/8").with(as(3L))).andExpect(status().isNotFound());
    }
    @Test void allRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/push/config")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/push/subscriptions/8")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/push/subscriptions/8")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/push/subscriptions").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }
    @Test void invalidInputDoesNotEchoSecrets() throws Exception {
        String body = mvc.perform(put("/api/push/subscriptions").with(as(2L)).contentType("application/json")
                .content("{\"endpoint\":\"https://127.0.0.1/SECRET\",\"p256dh\":\"SECRET\",\"auth\":\"SECRET\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SECRET", "127.0.0.1");
    }
    private RequestPostProcessor as(Long id) {
        var p = new MemberPrincipal(id, "test@example.com", Set.of(Role.ADMIN));
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }
}

package com.be.dashboard;

import com.be.dashboard.controller.DashboardController;
import com.be.dashboard.repository.DashboardRepository;
import com.be.dashboard.service.DashboardService;
import com.be.global.config.*;
import com.be.global.exception.GlobalExceptionHandler;
import com.be.global.security.*;
import com.be.member.enums.Role;
import com.be.security.JwtTestSupport;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import({DashboardService.class, SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class})
class DashboardApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean DashboardRepository repository;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService members;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds", () -> 300);
        registry.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }
    @Test void adminGetsSingleResponseWithEmptyDashboard() throws Exception {
        var principal = new MemberPrincipal(1L, "admin@example.com", Set.of(Role.ADMIN, Role.EMPLOYEE));
        mvc.perform(get("/api/admin/dashboard").with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overview.completionRate").value(0))
                .andExpect(jsonPath("$.distribution.counts.length()").value(5))
                .andExpect(jsonPath("$.activeCourses").isEmpty()).andExpect(jsonPath("$.recentAssignments").isEmpty());
    }
    @ParameterizedTest @EnumSource(value = Role.class, names = {"EMPLOYEE", "INSTRUCTOR"})
    void nonAdminCannotRead(Role role) throws Exception {
        var principal = new MemberPrincipal(2L, "other@example.com", Set.of(role));
        mvc.perform(get("/api/admin/dashboard").with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
}

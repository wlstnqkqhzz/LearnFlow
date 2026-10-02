package com.be.statistics;

import com.be.statistics.controller.StatisticsController;
import com.be.statistics.repository.StatisticsRepository;
import com.be.statistics.service.StatisticsService;
import com.be.global.config.*;
import com.be.global.exception.GlobalExceptionHandler;
import com.be.global.security.*;
import com.be.member.enums.Role;
import com.be.security.JwtTestSupport;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatisticsController.class)
@Import({StatisticsService.class, SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class})
class StatisticsApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean StatisticsRepository repository;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService members;
    @MockitoBean Clock clock;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("jwt.secret",JwtTestSupport::secret); r.add("jwt.access-token-ttl-seconds",()->300); r.add("jwt.refresh-token-ttl-seconds",()->3600);
    }
    @BeforeEach void setup() {
        var fixed = StatisticsIntegrationTest.CLOCK;
        when(clock.instant()).thenReturn(fixed.instant());
        when(clock.withZone(any())).thenAnswer(c -> fixed.withZone(c.getArgument(0)));
        when(repository.cohort(any())).thenReturn(StatisticsRepository.Cohort.EMPTY);
        when(repository.completions(any())).thenReturn(new StatisticsRepository.Completions(0,null));
    }
    RequestPostProcessor as(Role role) {
        var p = new MemberPrincipal(1L,"test@example.invalid",Set.of(role));
        return authentication(new UsernamePasswordAuthenticationToken(p,null,p.authorities()));
    }
    @ParameterizedTest @ValueSource(strings={"overview","trends","courses","departments"})
    void adminGetsMetaAndValidResponse(String endpoint) throws Exception {
        var result = mvc.perform(get("/api/admin/statistics/"+endpoint).with(as(Role.ADMIN))
                .param("startDate","2026-09-01").param("endDate","2026-09-30").param("courseId","2").param("departmentId","3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.meta.endDate").value("2026-09-30"))
                .andExpect(jsonPath("$.meta.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.meta.departmentScope").value("DIRECT"))
                .andExpect(jsonPath("$.meta.generatedAt").value("2026-10-02T03:00:00Z"))
                .andExpect(jsonPath("$.meta.courseId").value(2)).andExpect(jsonPath("$.meta.departmentId").value(3));
        if(endpoint.equals("overview")) result.andExpect(jsonPath("$.completionRate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.averageCompletionDays").value(org.hamcrest.Matchers.nullValue()));
        if(endpoint.equals("courses") || endpoint.equals("departments")) result.andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.content").isEmpty());
        if(endpoint.equals("trends")) result.andExpect(jsonPath("$.points.length()").value(30));
    }
    @ParameterizedTest @EnumSource(value=Role.class,names={"EMPLOYEE","INSTRUCTOR"})
    void nonAdminIsForbiddenForEveryEndpoint(Role role) throws Exception {
        for(String path : List.of("overview","trends","courses","departments"))
            mvc.perform(get("/api/admin/statistics/"+path).with(as(role)).param("startDate","2026-09-01").param("endDate","2026-09-30"))
                    .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void anonymousIsUnauthorized() throws Exception {
        for(String path : List.of("overview","trends","courses","departments"))
            mvc.perform(get("/api/admin/statistics/"+path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
    @ParameterizedTest @CsvSource({"2026-09-30,2026-09-01", "2024-01-01,2025-01-01", "2026-10-02,2026-10-03", "invalid,2026-09-30", "2025-02-29,2025-03-01"})
    void invalidDatesReturn400(String start, String end) throws Exception {
        mvc.perform(get("/api/admin/statistics/overview").with(as(Role.ADMIN)).param("startDate",start).param("endDate",end))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void missingDatesAreRequired() throws Exception {
        mvc.perform(get("/api/admin/statistics/overview").with(as(Role.ADMIN))).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @ParameterizedTest @CsvSource({"page,-1", "size,0", "size,101", "courseId,0", "departmentId,-1"})
    void invalidPagingAndIdsReturn400(String field,String value) throws Exception {
        mvc.perform(get("/api/admin/statistics/courses").with(as(Role.ADMIN)).param("startDate","2026-09-01")
                .param("endDate","2026-09-30").param(field,value)).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
}

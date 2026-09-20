package com.be.exam;

import com.be.exam.controller.ExamAttemptController;
import com.be.exam.dto.*;
import com.be.exam.enums.QuestionType;
import com.be.exam.service.ExamAttemptService;
import com.be.global.config.*;
import com.be.global.exception.*;
import com.be.global.security.*;
import com.be.member.enums.Role;
import com.be.security.JwtTestSupport;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

// 역할 게이트와 인증 주체 전달, 직원 전용 응답의 정답 비노출 계약 검증
@WebMvcTest(ExamAttemptController.class)
@Import({SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class})
class ExamAttemptApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExamAttemptService service;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService members;
    final MemberPrincipal owner = new MemberPrincipal(2L, "owner@example.com", Set.of(Role.EMPLOYEE));
    final MemberPrincipal other = new MemberPrincipal(3L, "other@example.com", Set.of(Role.EMPLOYEE));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds", () -> 300);
        registry.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @Test void startCreatesThenReusesWith201And200() throws Exception {
        when(service.start(10L, owner)).thenReturn(new ExamAttemptService.StartResult(summary(false), true),
                new ExamAttemptService.StartResult(summary(false), false));
        mvc.perform(post("/api/enrollments/10/exam-attempts").with(as(owner)))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/exam-attempts/30"));
        mvc.perform(post("/api/enrollments/10/exam-attempts").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").isEmpty()).andExpect(jsonPath("$.passed").isEmpty());
        verify(service, times(2)).start(10L, owner);
    }

    @Test void paperAnswerSubmitAndResultNeverSerializeAnswerKeys() throws Exception {
        when(service.paper(30L, owner)).thenReturn(new AttemptPaperResponse(30L, "시험", List.of(
                new AttemptPaperResponse.QuestionItem(1L, QuestionType.SINGLE_CHOICE, "문제", BigDecimal.TEN, 1,
                        List.of(new AttemptPaperResponse.ChoiceItem(100L, "A", 1)), List.of(100L)))));
        when(service.saveAnswer(eq(30L), eq(1L), eq(owner), any())).thenReturn(new AnswerResponse(1L, List.of(100L)));
        when(service.submit(30L, owner)).thenReturn(summary(true));
        when(service.result(30L, owner)).thenReturn(summary(true));
        when(service.history(10L, owner)).thenReturn(List.of(summary(true)));
        mvc.perform(get("/api/exam-attempts/30").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].sortOrder").value(1))
                .andExpect(content().string(not(containsString("\"correct"))))
                .andExpect(content().string(not(containsString("earnedScore"))));
        mvc.perform(put("/api/exam-attempts/30/answers/1").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedChoiceIds\":[100]}"))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("correct"))));
        mvc.perform(post("/api/exam-attempts/30/submit").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(80)).andExpect(content().string(not(containsString("correct"))));
        mvc.perform(get("/api/exam-attempts/30/result").with(as(owner))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("correct"))));
        mvc.perform(get("/api/enrollments/10/exam-attempts").with(as(owner))).andExpect(status().isOk());
    }

    @Test void ownershipFailuresFromServiceAre403() throws Exception {
        when(service.paper(30L, other)).thenThrow(new BusinessException(ErrorCode.EXAM_ACCESS_DENIED));
        when(service.submit(30L, other)).thenThrow(new BusinessException(ErrorCode.EXAM_ACCESS_DENIED));
        when(service.saveAnswer(eq(30L), eq(1L), eq(other), any())).thenThrow(new BusinessException(ErrorCode.EXAM_ACCESS_DENIED));
        mvc.perform(get("/api/exam-attempts/30").with(as(other))).andExpect(status().isForbidden());
        mvc.perform(post("/api/exam-attempts/30/submit").with(as(other))).andExpect(status().isForbidden());
        mvc.perform(put("/api/exam-attempts/30/answers/1").with(as(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedChoiceIds\":[100]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EXAM_ACCESS_DENIED"));
    }

    @Test void adminReadOnlyAndInstructorNoAcl() throws Exception {
        var admin = new MemberPrincipal(3L, "a@example.com", Set.of(Role.ADMIN));
        when(service.result(30L, admin)).thenReturn(summary(true));
        when(service.history(10L, admin)).thenReturn(List.of(summary(true)));
        mvc.perform(get("/api/exam-attempts/30/result").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/enrollments/10/exam-attempts").with(as(admin))).andExpect(status().isOk());
        mvc.perform(post("/api/exam-attempts/30/submit").with(as(admin))).andExpect(status().isForbidden());
        var instructor = new MemberPrincipal(4L, "i@example.com", Set.of(Role.INSTRUCTOR));
        mvc.perform(get("/api/exam-attempts/30/result").with(as(instructor))).andExpect(status().isForbidden());
        mvc.perform(post("/api/enrollments/10/exam-attempts").with(as(instructor))).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"selectedChoiceIds\":null}", "{\"selectedChoiceIds\":[null]}", "{\"selectedChoiceIds\":[0]}"})
    void invalidInputReturns400(String body) throws Exception {
        mvc.perform(put("/api/exam-attempts/30/answers/1").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(service);
    }

    @Test void anonymousMissingAndConflictStatuses() throws Exception {
        mvc.perform(get("/api/exam-attempts/30")).andExpect(status().isUnauthorized());
        when(service.paper(999L, owner)).thenThrow(new BusinessException(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));
        mvc.perform(get("/api/exam-attempts/999").with(as(owner))).andExpect(status().isNotFound());
        when(service.submit(30L, owner)).thenThrow(new BusinessException(ErrorCode.EXAM_ATTEMPT_ALREADY_SUBMITTED));
        mvc.perform(post("/api/exam-attempts/30/submit").with(as(owner))).andExpect(status().isConflict());
    }

    private RequestPostProcessor as(MemberPrincipal principal) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
    }
    private AttemptResponse summary(boolean submitted) {
        var now = LocalDateTime.of(2026, 9, 20, 0, 0);
        return new AttemptResponse(30L, 1, submitted ? new BigDecimal("80.00") : null, submitted ? true : null,
                now, submitted ? now.plusMinutes(1) : null, 3, 2);
    }
}

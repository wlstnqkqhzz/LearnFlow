package com.be.exam;

import com.be.exam.controller.*;
import com.be.exam.dto.*;
import com.be.exam.service.*;
import com.be.global.config.*;
import com.be.global.exception.*;
import com.be.global.security.*;
import com.be.security.JwtTestSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.be.exam.ExamFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 Security/MVC를 검증; 업무 조합은 Service 테스트에 위임
@WebMvcTest({ExamController.class, QuestionController.class})
@Import({SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "ADMIN")
class ExamManagementApiTest {
    static final String EXAM = "/api/courses/1/exam";
    static final String QUESTIONS = EXAM + "/questions";
    static final String CHOICES = QUESTIONS + "/1/choices";
    @Autowired MockMvc mvc;
    @MockitoBean ExamService exams;
    @MockitoBean QuestionService questions;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService members;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds", () -> 300);
        registry.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @Test void adminCreatesReadsPatchesAndValidatesExamButCannotDeleteExam() throws Exception {
        var response = new ExamResponse(10L, 1L, "시험", new BigDecimal("80"), 3, 1, BigDecimal.TEN);
        when(exams.create(eq(1L), any())).thenReturn(response);
        when(exams.get(1L)).thenReturn(response);
        when(exams.update(eq(1L), any())).thenReturn(response);
        when(exams.validateConfiguration(1L)).thenReturn(response);
        mvc.perform(post(EXAM).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"시험\",\"passingScore\":80,\"maxAttempts\":3}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", EXAM));
        mvc.perform(get(EXAM)).andExpect(status().isOk()).andExpect(jsonPath("$.totalQuestionScore").value(10));
        mvc.perform(patch(EXAM).contentType(MediaType.APPLICATION_JSON).content("{\"passingScore\":70}"))
                .andExpect(status().isOk());
        mvc.perform(get(EXAM + "/validation")).andExpect(status().isOk());
        mvc.perform(delete(EXAM)).andExpect(status().isMethodNotAllowed());
    }

    @Test void adminUsesQuestionCrudAndOrderEndpoints() throws Exception {
        var question = question(exam(), 1, com.be.exam.enums.QuestionType.SINGLE_CHOICE);
        var response = QuestionAdminResponse.from(question, choices(question));
        when(questions.create(eq(1L), any())).thenReturn(response);
        when(questions.getAll(1L)).thenReturn(List.of(response));
        when(questions.get(1L, 1L)).thenReturn(response);
        when(questions.update(eq(1L), eq(1L), any())).thenReturn(response);
        when(questions.reorder(eq(1L), any())).thenReturn(List.of(response));
        mvc.perform(post(QUESTIONS).contentType(MediaType.APPLICATION_JSON).content("""
                {"questionText":"문제","questionType":"SINGLE_CHOICE","score":10,"sortOrder":1,
                 "choices":[{"choiceText":"A","correct":true,"sortOrder":1}]}
                """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", QUESTIONS + "/1"));
        mvc.perform(get(QUESTIONS)).andExpect(status().isOk()).andExpect(jsonPath("$[0].choices[0].correct").value(true));
        mvc.perform(get(QUESTIONS + "/1")).andExpect(status().isOk());
        mvc.perform(patch(QUESTIONS + "/1").contentType(MediaType.APPLICATION_JSON).content("{\"correctChoiceIds\":[101]}"))
                .andExpect(status().isOk());
        mvc.perform(patch(QUESTIONS + "/order").contentType(MediaType.APPLICATION_JSON).content("{\"questionIds\":[1]}"))
                .andExpect(status().isOk());
        mvc.perform(delete(QUESTIONS + "/1")).andExpect(status().isNoContent());
        verify(questions).delete(1L, 1L);
    }

    @Test void adminUsesChoiceCrudAndOrderEndpoints() throws Exception {
        var response = new ChoiceAdminResponse(100L, "A", true, 1);
        when(questions.createChoice(eq(1L), eq(1L), any())).thenReturn(response);
        when(questions.getChoices(1L, 1L)).thenReturn(List.of(response));
        when(questions.getChoice(1L, 1L, 100L)).thenReturn(response);
        when(questions.updateChoice(eq(1L), eq(1L), eq(100L), any())).thenReturn(response);
        when(questions.reorderChoices(eq(1L), eq(1L), any())).thenReturn(List.of(response));
        mvc.perform(post(CHOICES).contentType(MediaType.APPLICATION_JSON).content("{\"choiceText\":\"A\",\"correct\":true,\"sortOrder\":1}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", CHOICES + "/100"));
        mvc.perform(get(CHOICES)).andExpect(status().isOk());
        mvc.perform(get(CHOICES + "/100")).andExpect(status().isOk());
        mvc.perform(patch(CHOICES + "/100").contentType(MediaType.APPLICATION_JSON).content("{\"choiceText\":\"B\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch(CHOICES + "/order").contentType(MediaType.APPLICATION_JSON).content("{\"choiceIds\":[100]}"))
                .andExpect(status().isOk());
        mvc.perform(delete(CHOICES + "/100")).andExpect(status().isNoContent());
    }

    @ParameterizedTest @ValueSource(strings = {"EMPLOYEE", "INSTRUCTOR"})
    void nonAdminsCannotAccessAnyManagementEndpoint(String role) throws Exception {
        for (String path : List.of(EXAM, EXAM + "/validation", QUESTIONS, QUESTIONS + "/1", CHOICES, CHOICES + "/100")) {
            mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for (String path : List.of(EXAM, QUESTIONS, CHOICES)) {
            mvc.perform(post(path).with(user("other").roles(role)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        for (String path : List.of(EXAM, QUESTIONS + "/1", QUESTIONS + "/order", CHOICES + "/100", CHOICES + "/order")) {
            mvc.perform(patch(path).with(user("other").roles(role)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        for (String path : List.of(QUESTIONS + "/1", CHOICES + "/100")) {
            mvc.perform(delete(path).with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(exams, questions);
    }

    @Test @WithAnonymousUser void anonymousGets401() throws Exception {
        mvc.perform(get(EXAM)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post(QUESTIONS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"title\":\"시험\",\"passingScore\":-1,\"maxAttempts\":1}",
            "{\"title\":\"시험\",\"passingScore\":101,\"maxAttempts\":1}",
            "{\"title\":\"시험\",\"passingScore\":70,\"maxAttempts\":0}"})
    void invalidExamInputReturns400(String body) throws Exception {
        mvc.perform(post(EXAM).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(exams);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "100000", "0.001"})
    void invalidRawScoreReturns400(String score) throws Exception {
        mvc.perform(post(QUESTIONS).contentType(MediaType.APPLICATION_JSON).content("""
                {"questionText":"문제","questionType":"SINGLE_CHOICE","score":%s,"sortOrder":1,
                 "choices":[{"choiceText":"A","correct":true,"sortOrder":1}]}
                """.formatted(score))).andExpect(status().isBadRequest());
        verifyNoInteractions(questions);
    }

    @Test void patchRejectsExplicitNullAndNestedInvalidIds() throws Exception {
        mvc.perform(patch(EXAM).contentType(MediaType.APPLICATION_JSON).content("{\"passingScore\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(QUESTIONS + "/1").contentType(MediaType.APPLICATION_JSON).content("{\"correctChoiceIds\":[null]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(CHOICES + "/order").contentType(MediaType.APPLICATION_JSON).content("{\"choiceIds\":[0]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(CHOICES + "/100").contentType(MediaType.APPLICATION_JSON).content("{\"correct\":null}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(exams, questions);
    }

    @Test void resourceAndBusinessErrorsUseCommonEnvelope() throws Exception {
        when(exams.get(1L)).thenThrow(new BusinessException(ErrorCode.EXAM_NOT_FOUND));
        mvc.perform(get(EXAM)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXAM_NOT_FOUND"));
        when(exams.get(99L)).thenThrow(new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        mvc.perform(get("/api/courses/99/exam")).andExpect(status().isNotFound());
        when(questions.get(1L, 99L)).thenThrow(new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        mvc.perform(get(QUESTIONS + "/99")).andExpect(status().isNotFound());
        when(exams.create(eq(1L), any())).thenThrow(new BusinessException(ErrorCode.DUPLICATE_EXAM));
        mvc.perform(post(EXAM).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"시험\",\"passingScore\":70,\"maxAttempts\":1}"))
                .andExpect(status().isConflict());
        when(exams.validateConfiguration(1L)).thenThrow(new BusinessException(ErrorCode.INVALID_EXAM_CONFIGURATION));
        mvc.perform(get(EXAM + "/validation")).andExpect(status().isBadRequest());
        doThrow(new BusinessException(ErrorCode.EXAM_HISTORY_DELETE_CONFLICT)).when(questions).delete(1L, 1L);
        mvc.perform(delete(QUESTIONS + "/1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors").isArray());
    }
}

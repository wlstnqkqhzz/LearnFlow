package com.be.retraining;

import com.be.course.dto.CourseResponse;
import com.be.course.enums.*;
import com.be.global.config.*;
import com.be.global.exception.*;
import com.be.global.security.*;
import com.be.retraining.controller.RetrainingPolicyController;
import com.be.retraining.dto.RetrainingPolicyResponse;
import com.be.retraining.service.*;
import com.be.security.JwtTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RetrainingPolicyController.class)
@Import({SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class})
class RetrainingApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean RetrainingPolicyService policies;
    @MockitoBean RetrainingOccurrenceService occurrences;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService members;
    static final String URL = "/api/retraining-policies";
    static final String BODY = """
        {"sourceCourseId":1,"baseTitle":"정보보안교육","enabled":true,"autoCreate":true,"autoOpen":false,
         "intervalMonths":12,"firstStartDate":"2027-01-31","durationDays":30,"generationLeadDays":7}
        """;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("jwt.secret", JwtTestSupport::secret);
        r.add("jwt.access-token-ttl-seconds", () -> 300);
        r.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }
    private RetrainingPolicyResponse policy() {
        return new RetrainingPolicyResponse(9L, 1L, "정보보안교육", true, true, false, 12,
                LocalDate.of(2027, 1, 31), 30, 7, 1, LocalDate.of(2027, 1, 24), null, null);
    }
    @Test void adminCrudPagingStatusAndSkipContract() throws Exception {
        when(policies.create(any())).thenReturn(policy());
        when(policies.get(9L)).thenReturn(policy());
        when(policies.update(eq(9L), any())).thenReturn(policy());
        when(policies.list(0, 20)).thenReturn(new PageImpl<>(List.of(policy()), PageRequest.of(0, 20), 1));
        when(policies.status(9L, false)).thenReturn(policy());
        when(policies.occurrences(9L, 0, 20)).thenReturn(Page.empty());
        when(occurrences.skip(9L, 1)).thenReturn(policy());
        mvc.perform(post(URL).with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string("Location", URL + "/9"))
                .andExpect(jsonPath("$.nextOccurrenceNumber").value(1)).andExpect(jsonPath("$.nextGenerationDate").value("2027-01-24"));
        mvc.perform(get(URL).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(9));
        mvc.perform(get(URL + "/9").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(patch(URL + "/9").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mvc.perform(patch(URL + "/9/status").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        mvc.perform(get(URL + "/9/courses").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(post(URL + "/9/skip-overdue").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"occurrenceNumber\":1}")).andExpect(status().isOk());
        verify(occurrences).skip(9L, 1);
    }
    @Test void occurrenceCreation201AndReplay200ReturnCourseIdentity() throws Exception {
        var course = new CourseResponse(100L, "교육 2027-01 · 1회차", null, CourseType.MANDATORY, CourseStatus.DRAFT,
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 3, 1), BigDecimal.TEN, null, null, null, null, 9L, 1);
        when(occurrences.generate(9L, 1)).thenReturn(new RetrainingOccurrenceService.Result(course, true), new RetrainingOccurrenceService.Result(course, false));
        mvc.perform(put(URL + "/9/occurrences/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/courses/100"))
                .andExpect(jsonPath("$.retrainingPolicyId").value(9)).andExpect(jsonPath("$.occurrenceNumber").value(1));
        mvc.perform(put(URL + "/9/occurrences/1").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(100));
    }
    @ParameterizedTest @ValueSource(strings = {"EMPLOYEE", "INSTRUCTOR"})
    void allPolicyRoutesAreAdminOnly(String role) throws Exception {
        for (var request : List.of(get(URL), get(URL + "/9"), get(URL + "/9/courses"), post(URL), patch(URL + "/9"), patch(URL + "/9/status"), put(URL + "/9/occurrences/1"), post(URL + "/9/skip-overdue")))
            mvc.perform(request.with(user("other").roles(role)).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(policies, occurrences);
    }
    @Test void invalidDtoIdsAndPagingRejectedBeforeMutation() throws Exception {
        for (String body : List.of("{}", BODY.replace("\"intervalMonths\":12", "\"intervalMonths\":0"),
                BODY.replace("\"durationDays\":30", "\"durationDays\":0"), BODY.replace("\"generationLeadDays\":7", "\"generationLeadDays\":-1"),
                BODY.replace("\"autoOpen\":false", "\"autoOpen\":null"), BODY.replace("정보보안교육", " ")))
            mvc.perform(post(URL).with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "?size=101").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(put(URL + "/9/occurrences/0").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(post(URL + "/9/skip-overdue").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(policies, occurrences);
    }
    @Test void conflictsAndNotFoundUseExistingErrorEnvelope() throws Exception {
        when(policies.get(9L)).thenThrow(new BusinessException(ErrorCode.RETRAINING_POLICY_NOT_FOUND));
        mvc.perform(get(URL + "/9").with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RETRAINING_POLICY_NOT_FOUND"));
        when(occurrences.generate(9L, 1)).thenThrow(new BusinessException(ErrorCode.RETRAINING_OVERDUE));
        mvc.perform(put(URL + "/9/occurrences/1").with(user("admin").roles("ADMIN"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETRAINING_OVERDUE"));
    }
}

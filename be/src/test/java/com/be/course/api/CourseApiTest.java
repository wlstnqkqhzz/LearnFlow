package com.be.course.api;

import com.be.course.controller.*;
import com.be.course.dto.*;
import com.be.course.enums.*;
import com.be.course.service.*;
import com.be.global.config.SecurityConfig;
import com.be.global.exception.*;
import com.be.global.security.*;
import com.be.security.JwtTestSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 Security 체인·MVC 검증·오류 응답 계약 확인, 비즈니스 규칙은 Service 테스트에서 검증
@WebMvcTest({CourseController.class, CourseContentController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "ADMIN")
class CourseApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean CourseService courses;
    @MockitoBean CourseContentService contents;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService authenticatedMembers;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds", () -> 300);
        registry.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @Test
    void adminCreatesDraftWithLocation() throws Exception {
        when(courses.create(any())).thenReturn(course());
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"교육\",\"courseType\":\"MANDATORY\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/courses/1"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.instructor.passwordHash").doesNotExist());
    }

    @Test
    void adminReadsCourseAndPaginatedList() throws Exception {
        when(courses.get(1L)).thenReturn(course());
        when(courses.search(any())).thenReturn(new PageImpl<>(List.of(course()), PageRequest.of(0, 20), 1));
        mvc.perform(get("/api/courses/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/courses").param("status", "DRAFT").param("type", "MANDATORY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void patchDistinguishesAbsentAndExplicitNull() throws Exception {
        when(courses.update(eq(1L), any())).thenReturn(course());
        mvc.perform(patch("/api/courses/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새 제목\",\"instructorId\":null,\"description\":null}"))
                .andExpect(status().isOk());
        var captured = ArgumentCaptor.forClass(CoursePatchRequest.class);
        verify(courses).update(eq(1L), captured.capture());
        assertThat(captured.getValue().isInstructorIdPresent()).isTrue();
        assertThat(captured.getValue().getInstructorId()).isNull();
        assertThat(captured.getValue().isDescriptionPresent()).isTrue();
        assertThat(captured.getValue().isStartDatePresent()).isFalse();
    }

    @Test
    void patchCannotSpoofInternalPresenceFlags() throws Exception {
        when(courses.update(eq(1L), any())).thenReturn(course());
        mvc.perform(patch("/api/courses/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructorIdPresent\":true,\"startDatePresent\":true}"))
                .andExpect(status().isOk());
        var captured = ArgumentCaptor.forClass(CoursePatchRequest.class);
        verify(courses).update(eq(1L), captured.capture());
        assertThat(captured.getValue().isInstructorIdPresent()).isFalse();
        assertThat(captured.getValue().isStartDatePresent()).isFalse();
    }

    @Test
    void delegatesStatusChange() throws Exception {
        when(courses.changeStatus(eq(1L), any())).thenReturn(course());
        mvc.perform(patch("/api/courses/1/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"OPEN\"}")).andExpect(status().isOk());
        verify(courses).changeStatus(1L, new CourseStatusRequest(CourseStatus.OPEN));
    }

    @Test
    void contentCrudAndOrderUseNestedCourseId() throws Exception {
        when(contents.create(eq(1L), any())).thenReturn(content());
        when(contents.getAll(1L)).thenReturn(List.of(content()));
        when(contents.get(1L, 10L)).thenReturn(content());
        when(contents.update(eq(1L), eq(10L), any())).thenReturn(content());
        when(contents.reorder(eq(1L), any())).thenReturn(List.of(content()));
        mvc.perform(post("/api/courses/1/contents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"1강\",\"contentType\":\"LINK\",\"contentUrl\":\"https://example.com\",\"sortOrder\":1}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/courses/1/contents/10"));
        mvc.perform(get("/api/courses/1/contents")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sortOrder").value(1));
        mvc.perform(get("/api/courses/1/contents/10")).andExpect(status().isOk());
        mvc.perform(patch("/api/courses/1/contents/10").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"required\":false,\"durationSeconds\":null}"))
                .andExpect(status().isOk());
        var patch = ArgumentCaptor.forClass(CourseContentPatchRequest.class);
        verify(contents).update(eq(1L), eq(10L), patch.capture());
        assertThat(patch.getValue().getRequired()).isFalse();
        assertThat(patch.getValue().isDurationSecondsPresent()).isTrue();
        mvc.perform(patch("/api/courses/1/contents/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentIds\":[10]}")).andExpect(status().isOk());
        verify(contents).reorder(1L, new ContentOrderRequest(List.of(10L)));
        mvc.perform(delete("/api/courses/1/contents/10"))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
        verify(contents).delete(1L, 10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\" \",\"courseType\":\"MANDATORY\"}",
            "{\"title\":\"교육\",\"courseType\":\"BAD\"}",
            "{\"title\":\"교육\",\"courseType\":\"OPTIONAL\",\"passingProgressRate\":101}",
            "{\"title\":\"교육\",\"courseType\":\"OPTIONAL\",\"passingProgressRate\":1.001}"})
    void invalidCourseCreateReturns400(String body) throws Exception {
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(courses);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":null}", "{\"title\":\" \"}", "{\"courseType\":null}",
            "{\"passingProgressRate\":null}", "{\"instructorId\":0}"})
    void invalidCoursePatchReturns400(String body) throws Exception {
        mvc.perform(patch("/api/courses/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(courses);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"sortOrder\":0}", "{\"required\":null}", "{\"durationSeconds\":-1}", "{\"contentUrl\":\" \"}"})
    void invalidContentPatchReturns400(String body) throws Exception {
        mvc.perform(patch("/api/courses/1/contents/10").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(contents);
    }

    @Test
    void invalidPaginationStatusAndOrderReturn400() throws Exception {
        mvc.perform(get("/api/courses").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/courses").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/courses").param("status", "BAD")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/courses/1/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BAD\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/courses/1/contents/order").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentIds\":[null]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(courses, contents);
    }

    @Test
    void businessErrorsHaveConsistentStatuses() throws Exception {
        when(courses.get(99L)).thenThrow(new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        mvc.perform(get("/api/courses/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        when(contents.get(1L, 99L)).thenThrow(new BusinessException(ErrorCode.COURSE_CONTENT_NOT_FOUND));
        mvc.perform(get("/api/courses/1/contents/99")).andExpect(status().isNotFound());
        when(courses.changeStatus(eq(1L), any())).thenThrow(new BusinessException(ErrorCode.INVALID_COURSE_STATUS_TRANSITION));
        mvc.perform(patch("/api/courses/1/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DRAFT\"}")).andExpect(status().isConflict());
        when(contents.reorder(eq(1L), any())).thenThrow(new BusinessException(ErrorCode.INVALID_CONTENT_ORDER));
        mvc.perform(patch("/api/courses/1/contents/order").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentIds\":[10,10]}")).andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotReadOrManageAdministrativeCourses() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isForbidden());
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/courses/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/courses/1/contents/10")).andExpect(status().isForbidden());
        verifyNoInteractions(courses, contents);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void instructorCanReadAllCoursesAndContentsButCannotEdit() throws Exception {
        when(courses.get(1L)).thenReturn(course());
        when(courses.search(any())).thenReturn(Page.empty());
        when(contents.getAll(1L)).thenReturn(List.of(content()));
        when(contents.get(1L, 10L)).thenReturn(content());
        mvc.perform(get("/api/courses")).andExpect(status().isOk());
        mvc.perform(get("/api/courses/1")).andExpect(status().isOk());
        mvc.perform(get("/api/courses/1/contents")).andExpect(status().isOk());
        mvc.perform(get("/api/courses/1/contents/10")).andExpect(status().isOk());
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/courses/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/courses/1/contents").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/courses/1/contents/order").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/courses/1/contents/10")).andExpect(status().isForbidden());
    }

    private CourseResponse course() {
        return new CourseResponse(1L, "교육", null, CourseType.MANDATORY, CourseStatus.DRAFT,
                null, null, new BigDecimal("100.00"), null, null, null, null);
    }

    @Test
    void courseDatesAndContentConflictsUseExistingErrorEnvelope() throws Exception {
        when(courses.changeStatus(eq(1L), any())).thenThrow(new BusinessException(ErrorCode.COURSE_DATES_REQUIRED));
        mvc.perform(patch("/api/courses/1/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COURSE_DATES_REQUIRED"));
        when(contents.update(eq(1L), eq(10L), any()))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_CONTENT_SORT_ORDER));
        mvc.perform(patch("/api/courses/1/contents/10").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CONTENT_SORT_ORDER"));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("FK restrict"))
                .when(contents).delete(1L, 10L);
        mvc.perform(delete("/api/courses/1/contents/10")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }

    @Test
    void invalidContentCreateAndCourseDeleteAreRejected() throws Exception {
        mvc.perform(post("/api/courses/1/contents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"sortOrder\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/courses/1")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(courses, contents);
    }

    private CourseContentResponse content() {
        return new CourseContentResponse(10L, 1L, "1강", ContentType.LINK, "https://example.com", null, 1, true, null, null);
    }
}

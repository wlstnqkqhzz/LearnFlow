package com.be.enrollment;

import com.be.course.repository.CourseContentRepository;
import com.be.enrollment.controller.ContentProgressController;
import com.be.enrollment.entity.ContentProgress;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.*;
import com.be.enrollment.service.*;
import com.be.exam.repository.ExamRepository;
import com.be.global.config.*;
import com.be.global.exception.GlobalExceptionHandler;
import com.be.global.security.*;
import com.be.member.enums.Role;
import com.be.security.JwtTestSupport;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static com.be.enrollment.ProgressFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 보안 체인·MVC·Service를 통과하여 본인 소유권 검증 누락 방지
@WebMvcTest(ContentProgressController.class)
@Import({SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class,
        ContentProgressService.class, EnrollmentCompletionService.class})
class ContentProgressApiTest {
    static final String READ = "/api/enrollments/10/progress";
    static final String UPDATE = "/api/enrollments/10/contents/1/progress";
    @Autowired MockMvc mvc;
    @MockitoBean EnrollmentRepository enrollments;
    @MockitoBean CourseContentRepository contents;
    @MockitoBean ContentProgressRepository progresses;
    @MockitoBean ExamRepository exams;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService authenticatedMembers;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds", () -> 300);
        registry.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @BeforeEach void setup() {
        var course = course();
        var enrollment = enrollment(course);
        var content = content(course, 1, true);
        when(enrollments.findById(10L)).thenReturn(Optional.of(enrollment));
        when(enrollments.findForProgressUpdate(10L)).thenReturn(Optional.of(enrollment));
        when(contents.findByIdAndCourseId(1L, 1L)).thenReturn(Optional.of(content));
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(content));
        doAnswer(call -> {
            ContentProgress progress = call.getArgument(0);
            doReturn(List.of(progress)).when(progresses).findByEnrollmentId(10L);
            return progress;
        }).when(progresses).saveAndFlush(any());
    }

    @Test void ownerReadsAndUpdatesWithConsistentDto() throws Exception {
        mvc.perform(get(READ).with(as(2, Role.EMPLOYEE))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contents[0].progressRate").value(0))
                .andExpect(jsonPath("$.contents[0].sortOrder").value(1))
                .andExpect(jsonPath("$.contents[0].required").value(true))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
        mvc.perform(patch(UPDATE).with(as(2, Role.EMPLOYEE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":35}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.progressRate").value(35))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isEmpty());
    }

    @Test void otherEmployeeCannotReadOrModify() throws Exception {
        mvc.perform(get(READ).with(as(3, Role.EMPLOYEE))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_PROGRESS_ACCESS_DENIED"));
        mvc.perform(patch(UPDATE).with(as(3, Role.EMPLOYEE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":35}")).andExpect(status().isForbidden());
        verifyNoInteractions(progresses);
    }

    @Test void adminCanReadButCannotModifyOtherEmployeeEvenWithEmployeeRole() throws Exception {
        mvc.perform(get(READ).with(as(3, Role.ADMIN))).andExpect(status().isOk());
        mvc.perform(patch(UPDATE).with(as(3, Role.ADMIN, Role.EMPLOYEE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":35}")).andExpect(status().isForbidden());
        mvc.perform(patch(UPDATE).with(as(3, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":35}")).andExpect(status().isForbidden());
        verify(progresses, never()).saveAndFlush(any());
    }

    @Test void adminEmployeeCanStudyOwnEnrollment() throws Exception {
        mvc.perform(patch(UPDATE).with(as(2, Role.ADMIN, Role.EMPLOYEE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":35}")).andExpect(status().isOk());
    }

    @Test void instructorWithoutEmployeeRoleHasNoStudentAccess() throws Exception {
        mvc.perform(get(READ).with(as(2, Role.INSTRUCTOR))).andExpect(status().isForbidden());
        mvc.perform(patch(UPDATE).with(as(2, Role.INSTRUCTOR)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":35}")).andExpect(status().isForbidden());
        verifyNoInteractions(enrollments);
    }

    @Test void anonymousReturns401() throws Exception {
        mvc.perform(get(READ)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(patch(UPDATE).contentType(MediaType.APPLICATION_JSON).content("{\"progressRate\":35}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"progressRate\":null}", "{\"progressRate\":-1}",
            "{\"progressRate\":100.01}", "{\"progressRate\":12.345}"})
    void invalidInputReturns400BeforeService(String body) throws Exception {
        mvc.perform(patch(UPDATE).with(as(2, Role.EMPLOYEE)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(enrollments);
    }

    @Test void missingEnrollmentAndMissingOrForeignContentReturn404() throws Exception {
        mvc.perform(get("/api/enrollments/999/progress").with(as(2, Role.EMPLOYEE)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
        mvc.perform(patch("/api/enrollments/10/contents/999/progress").with(as(2, Role.EMPLOYEE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"progressRate\":20}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COURSE_CONTENT_NOT_FOUND"));
    }

    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = {"COMPLETED", "FAILED", "EXPIRED"})
    void terminalEnrollmentReturns409(EnrollmentStatus status) throws Exception {
        var enrollment = enrollments.findById(10L).orElseThrow();
        ReflectionTestUtils.setField(enrollment, "status", status);
        mvc.perform(patch(UPDATE).with(as(2, Role.EMPLOYEE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":20}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENROLLMENT_PROGRESS_NOT_EDITABLE"));
    }

    @Test void optimisticLockConflictUsesCommon409Response() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(ContentProgress.class, 1L))
                .when(progresses).saveAndFlush(any());
        mvc.perform(patch(UPDATE).with(as(2, Role.EMPLOYEE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":20}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test void concurrentInsertUsesCommonIntegrity409Response() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate progress"))
                .when(progresses).saveAndFlush(any());
        mvc.perform(patch(UPDATE).with(as(2, Role.EMPLOYEE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":20}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }

    @Test void completionTimestampCannotBeChosenByClient() throws Exception {
        when(exams.existsByCourseId(1L)).thenReturn(true);
        mvc.perform(patch(UPDATE).with(as(2, Role.EMPLOYEE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressRate\":100,\"completedAt\":\"2099-01-01T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contents[0].completedAt").isNotEmpty())
                .andExpect(jsonPath("$.contents[0].completedAt").value(
                        org.hamcrest.Matchers.not("2099-01-01T00:00:00")));
    }

    private RequestPostProcessor as(long memberId, Role... roles) {
        var principal = new MemberPrincipal(memberId, "user@example.com", Set.of(roles));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
    }
}

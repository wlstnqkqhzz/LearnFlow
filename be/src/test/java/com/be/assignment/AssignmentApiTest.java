package com.be.assignment;

import com.be.assignment.controller.AssignmentRuleController;
import com.be.assignment.dto.*;
import com.be.assignment.enums.AssignmentRuleType;
import com.be.assignment.service.AssignmentRuleService;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.enrollment.controller.EnrollmentController;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.enrollment.service.EnrollmentService;
import com.be.global.config.*;
import com.be.global.exception.*;
import com.be.global.security.*;
import com.be.member.enums.Role;
import com.be.member.repository.MemberRepository;
import com.be.security.JwtTestSupport;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.be.assignment.AssignmentFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 Security/MVC 및 EnrollmentService로 권한과 본인 조회 범위 검증
@WebMvcTest({AssignmentRuleController.class, EnrollmentController.class})
@Import({SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class, EnrollmentService.class})
@WithMockUser(roles = "ADMIN")
class AssignmentApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean AssignmentRuleService rules;
    @MockitoBean EnrollmentRepository enrollments;
    @MockitoBean com.be.notification.service.NotificationService notifications;
    @MockitoBean CourseRepository courses;
    @MockitoBean MemberRepository members;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService authenticatedMembers;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds", () -> 300);
        registry.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @Test
    void adminCreatesRuleWithLocationAndUsesNestedPaths() throws Exception {
        when(rules.create(eq(1L), any())).thenReturn(ruleResponse());
        when(rules.getAll(1L)).thenReturn(List.of(ruleResponse()));
        when(rules.get(1L, 100L)).thenReturn(ruleResponse());
        when(rules.update(eq(1L), eq(100L), any())).thenReturn(ruleResponse());
        when(rules.changeStatus(eq(1L), eq(100L), any())).thenReturn(ruleResponse());
        mvc.perform(post("/api/courses/1/assignment-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"ALL_EMPLOYEES\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/courses/1/assignment-rules/100"));
        mvc.perform(get("/api/courses/1/assignment-rules")).andExpect(status().isOk());
        mvc.perform(get("/api/courses/1/assignment-rules/100")).andExpect(status().isOk());
        mvc.perform(patch("/api/courses/1/assignment-rules/100").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleType\":\"ALL_EMPLOYEES\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/courses/1/assignment-rules/100/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isOk());
        verify(rules).changeStatus(1L, 100L, new AssignmentRuleStatusRequest(false));
        mvc.perform(delete("/api/courses/1/assignment-rules/100")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void adminManuallyAssignsAndReadsDetailAndCoursePage() throws Exception {
        var course = course(CourseStatus.OPEN);
        var member = member(2L);
        var value = Enrollment.manual(member, course, LocalDateTime.now(CLOCK));
        id(value, 50L);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));
        doAnswer(call -> { id(call.getArgument(0), 50L); return call.getArgument(0); }).when(enrollments).saveAndFlush(any());
        when(enrollments.findById(50L)).thenReturn(Optional.of(value));
        when(courses.existsById(1L)).thenReturn(true);
        when(enrollments.search(eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(value), PageRequest.of(0, 20), 1));
        mvc.perform(post("/api/courses/1/enrollments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":2}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/enrollments/50"))
                .andExpect(jsonPath("$.assignmentSource").value("MANUAL"))
                .andExpect(jsonPath("$.assignmentRuleId").isEmpty()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        mvc.perform(get("/api/enrollments/50")).andExpect(status().isOk());
        mvc.perform(get("/api/courses/1/enrollments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void meAlwaysUsesPrincipalEvenWhenAnotherMemberIdIsSupplied() throws Exception {
        var course = course(CourseStatus.OPEN);
        for (long memberId : new long[] {2L, 3L}) {
            var value = Enrollment.manual(member(memberId), course, LocalDateTime.now(CLOCK));
            id(value, memberId + 50);
            when(enrollments.search(isNull(), eq(memberId), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(value), PageRequest.of(0, 20), 1));
            MemberPrincipal principal = new MemberPrincipal(memberId, "user@example.com", Set.of(Role.EMPLOYEE));
            var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
            mvc.perform(get("/api/enrollments/me").param("memberId", "999").with(authentication(auth)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].memberId").value(memberId));
            verify(enrollments).search(isNull(), eq(memberId), isNull(), any(Pageable.class));
        }
        verify(enrollments, never()).search(any(), eq(999L), any(), any());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotManageOrReadOthers() throws Exception {
        mvc.perform(post("/api/courses/1/assignment-rules").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/courses/1/assignment-rules")).andExpect(status().isForbidden());
        mvc.perform(post("/api/courses/1/enrollments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/courses/1/enrollments")).andExpect(status().isForbidden());
        mvc.perform(get("/api/enrollments/50")).andExpect(status().isForbidden());
        mvc.perform(get("/api/members/999/enrollments")).andExpect(status().isForbidden());
        verifyNoInteractions(rules, enrollments, courses, members);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void instructorCourseReadPermissionDoesNotExposeManagementChildren() throws Exception {
        mvc.perform(get("/api/courses/1/assignment-rules")).andExpect(status().isForbidden());
        mvc.perform(get("/api/courses/1/assignment-rules/100")).andExpect(status().isForbidden());
        mvc.perform(get("/api/courses/1/enrollments")).andExpect(status().isForbidden());
        mvc.perform(get("/api/enrollments/50")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsReturn401() throws Exception {
        mvc.perform(post("/api/courses/1/enrollments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/enrollments/me")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"ruleType\":\"BAD\"}", "{\"ruleType\":\"NEW_EMPLOYEE\",\"newEmployeeDays\":0}",
            "{\"ruleType\":\"NEW_EMPLOYEE\",\"newEmployeeDays\":32768}"})
    void invalidRuleInputReturns400(String body) throws Exception {
        mvc.perform(post("/api/courses/1/assignment-rules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(rules);
    }

    @Test
    void invalidEnrollmentAndPageInputReturns400() throws Exception {
        mvc.perform(post("/api/courses/1/enrollments").contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/courses/1/enrollments").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/courses/1/enrollments").param("status", "BAD")).andExpect(status().isBadRequest());
        verifyNoInteractions(enrollments, courses, members);
    }

    @Test
    void missingResourcesAndDuplicateAndLockConflictsUseExistingEnvelope() throws Exception {
        when(rules.get(1L, 999L)).thenThrow(new BusinessException(ErrorCode.ASSIGNMENT_RULE_NOT_FOUND));
        mvc.perform(get("/api/courses/1/assignment-rules/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/enrollments/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
        var course = course(CourseStatus.OPEN);
        var member = member(2L);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));
        when(enrollments.findExistingForAssignment(2L, 1L)).thenReturn(Optional.of(Enrollment.manual(member, course, LocalDateTime.now(CLOCK))));
        mvc.perform(post("/api/courses/1/enrollments").contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ENROLLMENT"));
        when(rules.changeStatus(eq(1L), eq(100L), any())).thenThrow(new org.springframework.dao.CannotAcquireLockException("lock"));
        mvc.perform(patch("/api/courses/1/assignment-rules/100/status").contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    private AssignmentRuleResponse ruleResponse() {
        return AssignmentRuleResponse.from(rule(course(CourseStatus.OPEN), AssignmentRuleType.ALL_EMPLOYEES));
    }
}

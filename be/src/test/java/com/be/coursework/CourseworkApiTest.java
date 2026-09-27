package com.be.coursework;

import com.be.coursework.controller.*;
import com.be.coursework.dto.*;
import com.be.coursework.repository.*;
import com.be.coursework.service.*;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.enrollment.service.EnrollmentCompletionService;
import com.be.global.config.*;
import com.be.global.exception.*;
import com.be.global.security.*;
import com.be.member.repository.MemberRepository;
import com.be.member.enums.Role;
import com.be.security.JwtTestSupport;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
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
import static com.be.coursework.CourseworkFixtures.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AssignmentController.class,AssignmentSubmissionController.class})
@Import({SecurityConfig.class,MemberSupportConfig.class,GlobalExceptionHandler.class,
    AssignmentService.class,AssignmentSubmissionService.class,CourseworkAccess.class,AssignmentPolicy.class})
class CourseworkApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean CourseRepository courses;
    @MockitoBean AssignmentRepository assignments;
    @MockitoBean AssignmentSubmissionRepository submissions;
    @MockitoBean EnrollmentRepository enrollments;
    @MockitoBean MemberRepository members;
    @MockitoBean EnrollmentCompletionService completion;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService authenticatedMembers;
    @MockitoBean Clock clock;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret",JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds",()->300);
        registry.add("jwt.refresh-token-ttl-seconds",()->3600);
    }
    @BeforeEach void setup() {
        var c = course(CourseStatus.DRAFT);
        when(courses.findById(1L)).thenReturn(Optional.of(c));
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(c));
        var e = enrollment(c);
        when(enrollments.findById(10L)).thenReturn(Optional.of(e));
        when(enrollments.findForCourseworkUpdate(10L)).thenReturn(Optional.of(e));
        when(clock.instant()).thenReturn(CLOCK.instant());
        when(clock.withZone(any())).thenReturn(CLOCK);
    }
    @Test void adminAndAssignedInstructorCanManageButOtherInstructorCannot() throws Exception {
        when(assignments.saveAndFlush(any())).thenAnswer(c -> {
            var a = c.getArgument(0);
            com.be.assignment.AssignmentFixtures.id(a,20L); return a;
        });
        for(var p:List.of(ADMIN,INSTRUCTOR)) mvc.perform(post("/api/courses/1/assignments").with(as(p))
            .contentType(MediaType.APPLICATION_JSON).content(createBody()))
            .andExpect(status().isCreated()).andExpect(header().string("Location","/api/courses/1/assignments/20"))
            .andExpect(jsonPath("$.assignmentId").value(20));
        mvc.perform(get("/api/courses/1/assignments").with(as(principal(99,Role.INSTRUCTOR))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ASSIGNMENT_ACCESS_DENIED"));
        mvc.perform(post("/api/courses/1/assignments").with(as(OWNER)).contentType(MediaType.APPLICATION_JSON).content(createBody()))
            .andExpect(status().isForbidden());
    }
    @Test void employeeRoutesCheckOwnershipAndAdminIsNotEmployeeOverride() throws Exception {
        mvc.perform(get("/api/enrollments/10/assignments").with(as(OWNER))).andExpect(status().isOk());
        mvc.perform(get("/api/enrollments/10/assignments").with(as(principal(99,Role.EMPLOYEE))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/enrollments/10/assignments").with(as(ADMIN))).andExpect(status().isForbidden());
        mvc.perform(get("/api/enrollments/10/assignments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/assignment-submissions/30").with(as(OWNER))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"submissionType\":\"FILE\",\"content\":\"x\"}",
        "{\"submissionType\":\"TEXT\",\"content\":\" \"}", "{\"submissionType\":null,\"content\":\"x\"}"})
    void invalidSubmissionDtoIs400(String body) throws Exception {
        mvc.perform(put("/api/enrollments/10/assignments/20/submission").with(as(OWNER))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verify(submissions,never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(strings={"-1","100.01","80.001"})
    void invalidGradeScoreRejectedBeforeMutation(String score) throws Exception {
        mvc.perform(patch("/api/assignment-submissions/30/grade").with(as(ADMIN)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"score\":"+score+"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(submissions);
    }
    @Test void patchDistinguishesOmittedFieldsFromExplicitNull() throws Exception {
        var c = course(CourseStatus.DRAFT); var a = assignment(c,20,true);
        when(assignments.findByIdAndCourseId(20L,1L)).thenReturn(Optional.of(a));
        mvc.perform(patch("/api/courses/1/assignments/20").with(as(ADMIN)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"description\":null}")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("과제"));
        mvc.perform(patch("/api/courses/1/assignments/20").with(as(ADMIN)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"dueDate\":null}")).andExpect(status().isBadRequest());
    }
    @Test void deadlineAndVersionConflictsUseExistingErrorContract() throws Exception {
        var c = course(CourseStatus.OPEN); var a = assignment(c,20,true);
        when(assignments.findByIdAndCourseId(20L,1L)).thenReturn(Optional.of(a));
        when(enrollments.findForCourseworkUpdate(10L)).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("conflict"));
        mvc.perform(put("/api/enrollments/10/assignments/20/submission").with(as(OWNER)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"submissionType\":\"TEXT\",\"content\":\"answer\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }
    String createBody() { return "{\"title\":\"과제\",\"required\":true,\"dueDate\":\"2026-09-27\",\"passingScore\":80,\"sortOrder\":1}"; }
    RequestPostProcessor as(MemberPrincipal p) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.authorities()));
    }
}

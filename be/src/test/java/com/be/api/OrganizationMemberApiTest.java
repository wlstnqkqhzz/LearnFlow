package com.be.api;

import com.be.global.config.LocalApiSecurityConfig;
import com.be.global.exception.*;
import com.be.member.controller.MemberController;
import com.be.member.dto.*;
import com.be.member.enums.*;
import com.be.member.service.MemberService;
import com.be.organization.controller.*;
import com.be.organization.dto.*;
import com.be.organization.service.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 로컬 프로필의 실제 MVC·Validation·Security 필터를 통과하는 HTTP 계약 테스트
@WebMvcTest({DepartmentController.class, JobPositionController.class, MemberController.class})
@Import({GlobalExceptionHandler.class, LocalApiSecurityConfig.class})
@ActiveProfiles("local-api")
class OrganizationMemberApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean DepartmentService departments;
    @MockitoBean JobPositionService positions;
    @MockitoBean MemberService members;

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 18, 1, 0);

    @Test
    void createsDepartmentWithLocation() throws Exception {
        when(departments.create(any())).thenReturn(department());
        mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEV\",\"name\":\"개발팀\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/departments/1"))
                .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.code").value("DEV"));
    }

    @Test
    void createsPositionWithLocation() throws Exception {
        when(positions.create(any())).thenReturn(position());
        mvc.perform(post("/api/job-positions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BACKEND\",\"name\":\"개발자\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/job-positions/2"));
    }

    @Test
    void createsMemberWithoutExposingPasswords() throws Exception {
        when(members.create(any())).thenReturn(member());
        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content("""
                {"employeeNumber":"E001","email":" KIM@Example.com ","password":"password123!",
                 "name":"김개발","departmentId":1,"jobPositionId":2,"hireDate":"2026-09-18"}
                """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/members/3"))
                .andExpect(jsonPath("$.email").value("kim@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(members).create(argThat(request -> request.email().equals("kim@example.com")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/departments", "/api/job-positions", "/api/members"})
    void rejectsInvalidCreateBody(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
        verifyNoInteractions(departments, positions, members);
    }

    @Test
    void getsSingleResources() throws Exception {
        when(departments.get(1L)).thenReturn(department());
        when(positions.get(2L)).thenReturn(position());
        when(members.get(3L)).thenReturn(member());
        mvc.perform(get("/api/departments/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/job-positions/2")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2));
        mvc.perform(get("/api/members/3")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void getsAllAndActiveOrganizationLists() throws Exception {
        when(departments.getAll()).thenReturn(List.of(department()));
        when(departments.getActive()).thenReturn(List.of(department()));
        when(positions.getAll()).thenReturn(List.of(position()));
        when(positions.getActive()).thenReturn(List.of(position()));
        mvc.perform(get("/api/departments")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/departments").param("active", "true")).andExpect(status().isOk());
        mvc.perform(get("/api/job-positions").param("active", "false")).andExpect(status().isOk());
        mvc.perform(get("/api/job-positions").param("active", "true")).andExpect(status().isOk());
        verify(departments).getAll();
        verify(departments).getActive();
        verify(positions).getAll();
        verify(positions).getActive();
    }

    @Test
    void getsPagedFilteredMembers() throws Exception {
        when(members.search(any())).thenReturn(new PageImpl<>(List.of(member()),
                PageRequest.of(2, 5), 20));
        mvc.perform(get("/api/members").param("page", "2").param("size", "5")
                        .param("name", "김").param("departmentId", "1")
                        .param("jobPositionId", "2").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5)).andExpect(jsonPath("$.totalElements").value(20))
                .andExpect(jsonPath("$.totalPages").value(4)).andExpect(jsonPath("$.content[0].id").value(3));
        verify(members).search(argThat(request -> request.page() == 2 && request.size() == 5
                && request.name().equals("김") && request.departmentId() == 1L
                && request.jobPositionId() == 2L && request.status() == MemberStatus.ACTIVE));
    }

    @Test
    void usesDefaultPagination() throws Exception {
        when(members.search(any())).thenReturn(Page.empty(PageRequest.of(0, 20)));
        mvc.perform(get("/api/members")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(members).search(argThat(request -> request.page() == 0 && request.size() == 20));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=text",
            "status=UNKNOWN", "departmentId=-1", "jobPositionId=0"})
    void rejectsInvalidSearchParameters(String query) throws Exception {
        String[] pair = query.split("=");
        mvc.perform(get("/api/members").param(pair[0], pair[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(members);
    }

    @Test
    void distinguishesMissingAndNullParentInPatch() throws Exception {
        when(departments.patch(eq(1L), any())).thenReturn(department());
        mvc.perform(patch("/api/departments/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"변경\"}")).andExpect(status().isOk());
        verify(departments).patch(eq(1L), argThat(request -> !request.isParentDepartmentSpecified()));
        mvc.perform(patch("/api/departments/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentDepartmentId\":null}")).andExpect(status().isOk());
        verify(departments).patch(eq(1L), argThat(request ->
                request.isParentDepartmentSpecified() && request.getParentDepartmentId() == null));
    }

    @Test
    void patchesPositionAndOrganizationStatus() throws Exception {
        when(positions.update(eq(2L), any())).thenReturn(position());
        when(departments.deactivate(1L)).thenReturn(department());
        when(departments.activate(1L)).thenReturn(department());
        when(positions.deactivate(2L)).thenReturn(position());
        when(positions.activate(2L)).thenReturn(position());
        mvc.perform(patch("/api/job-positions/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새 직무\"}")).andExpect(status().isOk());
        for (boolean active : List.of(true, false)) {
            mvc.perform(patch("/api/departments/1/status").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":" + active + "}")).andExpect(status().isOk());
            mvc.perform(patch("/api/job-positions/2/status").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":" + active + "}")).andExpect(status().isOk());
        }
        verify(departments).activate(1L);
        verify(departments).deactivate(1L);
        verify(positions).activate(2L);
        verify(positions).deactivate(2L);
    }

    @Test
    void patchesOnlyMemberName() throws Exception {
        when(members.patch(eq(3L), any())).thenReturn(member());
        mvc.perform(patch("/api/members/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새 이름\"}")).andExpect(status().isOk());
        verify(members).patch(eq(3L), argThat(request ->
                request.getName().equals("새 이름") && request.getEmail() == null && request.getHireDate() == null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":null}", "{\"email\":null}", "{\"hireDate\":null}",
            "{\"name\":\"  \"}", "{\"email\":\"invalid\"}", "{\"hireDate\":\"wrong-date\"}"})
    void rejectsInvalidMemberPatch(String body) throws Exception {
        mvc.perform(patch("/api/members/3").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(members);
    }

    @Test
    void routesMemberOrganizationAndStatusChanges() throws Exception {
        when(members.changeDepartment(eq(3L), any())).thenReturn(member());
        when(members.changeJobPosition(eq(3L), any())).thenReturn(member());
        when(members.changeStatus(eq(3L), any())).thenReturn(member());
        mvc.perform(patch("/api/members/3/department").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":1}")).andExpect(status().isOk());
        mvc.perform(patch("/api/members/3/job-position").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPositionId\":2}")).andExpect(status().isOk());
        mvc.perform(patch("/api/members/3/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_LEAVE\"}")).andExpect(status().isOk());
        verify(members).changeStatus(3L, new MemberStatusUpdateRequest(MemberStatus.ON_LEAVE));
    }

    @Test
    void routesRoleAdditionAndRemoval() throws Exception {
        when(members.addRole(eq(3L), any())).thenReturn(member());
        when(members.removeRole(eq(3L), any())).thenReturn(member());
        mvc.perform(post("/api/members/3/roles/INSTRUCTOR")).andExpect(status().isOk());
        mvc.perform(post("/api/members/3/roles/ADMIN")).andExpect(status().isOk());
        mvc.perform(delete("/api/members/3/roles/INSTRUCTOR")).andExpect(status().isOk());
        verify(members).addRole(3L, new MemberRoleUpdateRequest(Role.INSTRUCTOR));
        verify(members).removeRole(3L, new MemberRoleUpdateRequest(Role.INSTRUCTOR));
    }

    @Test
    void rejectsEmployeeRoleRemovalAsBusinessError() throws Exception {
        when(members.removeRole(3L, new MemberRoleUpdateRequest(Role.EMPLOYEE)))
                .thenThrow(new BusinessException(ErrorCode.REQUIRED_EMPLOYEE_ROLE));
        mvc.perform(delete("/api/members/3/roles/EMPLOYEE")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_EMPLOYEE_ROLE"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "EMPLOYEE"})
    void rejectsUnsupportedRoleAddition(String role) throws Exception {
        mvc.perform(post("/api/members/3/roles/" + role)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(members);
    }

    @Test
    void rejectsInvalidStateEnum() throws Exception {
        mvc.perform(patch("/api/members/3/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(members);
    }

    @Test
    void returnsNotFoundForEachResource() throws Exception {
        when(departments.get(99L)).thenThrow(new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        when(positions.get(99L)).thenThrow(new BusinessException(ErrorCode.JOB_POSITION_NOT_FOUND));
        when(members.get(99L)).thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        for (String path : List.of("departments", "job-positions", "members")) {
            mvc.perform(get("/api/" + path + "/99")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.errors").isArray());
        }
    }

    @Test
    void returnsConflictForDuplicateData() throws Exception {
        when(departments.create(any())).thenThrow(new BusinessException(ErrorCode.DUPLICATE_DEPARTMENT_CODE));
        mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEV\",\"name\":\"개발팀\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_DEPARTMENT_CODE"));
    }

    @Test
    void returnsConflictForInvalidStateTransition() throws Exception {
        when(members.changeStatus(eq(3L), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_MEMBER_STATUS_TRANSITION));
        mvc.perform(patch("/api/members/3/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_MEMBER_STATUS_TRANSITION"));
    }

    @Test
    void hidesUnexpectedServerDetails() throws Exception {
        when(members.get(3L)).thenThrow(new IllegalStateException("internal-secret"));
        mvc.perform(get("/api/members/3")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-secret"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/members/0", "/api/members/no-number", "/api/members/3/roles/UNKNOWN"})
    void rejectsInvalidPathValues(String path) throws Exception {
        if (path.contains("/roles/")) {
            mvc.perform(delete(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        } else {
            mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        }
        verifyNoInteractions(members);
    }

    @Test
    void returnsConsistentFrameworkErrors() throws Exception {
        mvc.perform(put("/api/members/3")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/members").contentType(MediaType.TEXT_PLAIN).content("bad"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void requiresActivationValue() throws Exception {
        mvc.perform(patch("/api/departments/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(departments);
    }

    @Test
    void doesNotExposeOtherPathsThroughLocalProfile() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
    }

    private DepartmentResponse department() {
        return new DepartmentResponse(1L, "DEV", "개발팀", null, true, now, now);
    }

    private JobPositionResponse position() {
        return new JobPositionResponse(2L, "BACKEND", "개발자", true, now, now);
    }

    private MemberResponse member() {
        return new MemberResponse(3L, "E001", "kim@example.com", "김개발", 1L, 2L,
                MemberStatus.ACTIVE, LocalDate.of(2026, 9, 18), null, Set.of(Role.EMPLOYEE), now, now);
    }
}

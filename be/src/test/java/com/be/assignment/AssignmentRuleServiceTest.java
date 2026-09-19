package com.be.assignment;

import com.be.assignment.dto.*;
import com.be.assignment.entity.AssignmentRule;
import com.be.assignment.enums.AssignmentRuleType;
import com.be.assignment.repository.AssignmentRuleRepository;
import com.be.assignment.service.*;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.global.exception.*;
import com.be.organization.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.be.assignment.AssignmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 규칙 조건 조합 및 소속·활성 전환 계약 검증
@ExtendWith(MockitoExtension.class)
class AssignmentRuleServiceTest {
    @Mock AssignmentRuleRepository rules;
    @Mock CourseRepository courses;
    @Mock DepartmentRepository departments;
    @Mock JobPositionRepository positions;
    @Mock AutoAssignmentService auto;
    AssignmentRuleService service;

    @BeforeEach
    void setUp() { service = new AssignmentRuleService(rules, courses, departments, positions, auto); }

    @ParameterizedTest
    @EnumSource(AssignmentRuleType.class)
    void createsAllSupportedRuleTypes(AssignmentRuleType type) {
        lock();
        if (type == AssignmentRuleType.DEPARTMENT) when(departments.findById(10L)).thenReturn(Optional.of(department(10L)));
        if (type == AssignmentRuleType.JOB_POSITION) when(positions.findById(20L)).thenReturn(Optional.of(position(20L)));
        doAnswer(call -> { id(call.getArgument(0), 100L); return call.getArgument(0); }).when(rules).saveAndFlush(any());
        var response = service.create(1L, new AssignmentRuleCreateRequest(type,
                type == AssignmentRuleType.DEPARTMENT ? 10L : null,
                type == AssignmentRuleType.JOB_POSITION ? 20L : null,
                type == AssignmentRuleType.NEW_EMPLOYEE ? 90 : null, null));
        assertThat(response.ruleType()).isEqualTo(type);
        assertThat(response.active()).isTrue();
        verify(auto).assignRule(1L, 100L);
    }

    @ParameterizedTest
    @CsvSource(value = {"ALL_EMPLOYEES,10,null,null", "DEPARTMENT,null,null,null", "DEPARTMENT,10,20,null",
            "JOB_POSITION,10,20,null", "JOB_POSITION,null,null,null", "NEW_EMPLOYEE,null,null,null",
            "NEW_EMPLOYEE,10,null,90", "ALL_EMPLOYEES,null,null,90"}, nullValues = "null")
    void rejectsInvalidTargetCombination(AssignmentRuleType type, Long department, Long position, Integer days) {
        lock();
        assertCode(ErrorCode.INVALID_ASSIGNMENT_RULE_TARGET,
                () -> service.create(1L, new AssignmentRuleCreateRequest(type, department, position, days, true)));
        verifyNoInteractions(auto);
        verify(rules, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 32768})
    void rejectsInvalidDaysAtServiceBoundary(int days) {
        lock();
        assertCode(ErrorCode.INVALID_NEW_EMPLOYEE_DAYS, () -> service.create(1L,
                new AssignmentRuleCreateRequest(AssignmentRuleType.NEW_EMPLOYEE, null, null, days, true)));
    }

    @Test
    void rejectsInactiveDepartmentAndJob() {
        lock();
        var department = department(10L);
        department.deactivate();
        var position = position(20L);
        position.deactivate();
        when(departments.findById(10L)).thenReturn(Optional.of(department));
        when(positions.findById(20L)).thenReturn(Optional.of(position));
        assertCode(ErrorCode.INACTIVE_DEPARTMENT, () -> service.create(1L,
                new AssignmentRuleCreateRequest(AssignmentRuleType.DEPARTMENT, 10L, null, null, true)));
        assertCode(ErrorCode.INACTIVE_JOB_POSITION, () -> service.create(1L,
                new AssignmentRuleCreateRequest(AssignmentRuleType.JOB_POSITION, null, 20L, null, true)));
    }

    @Test
    void rejectsMissingTargetsAndCourse() {
        assertCode(ErrorCode.COURSE_NOT_FOUND, () -> service.create(99L, all(true)));
        lock();
        assertCode(ErrorCode.DEPARTMENT_NOT_FOUND, () -> service.create(1L,
                new AssignmentRuleCreateRequest(AssignmentRuleType.DEPARTMENT, 99L, null, null, true)));
        assertCode(ErrorCode.JOB_POSITION_NOT_FOUND, () -> service.create(1L,
                new AssignmentRuleCreateRequest(AssignmentRuleType.JOB_POSITION, null, 99L, null, true)));
    }

    @Test
    void inactiveCreateDoesNotTriggerAssignment() {
        lock();
        assertThat(service.create(1L, all(false)).active()).isFalse();
        verifyNoInteractions(auto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"get", "update", "status"})
    void rejectsForeignCourseRule(String operation) {
        if (!operation.equals("get")) lock();
        assertCode(ErrorCode.ASSIGNMENT_RULE_NOT_FOUND, () -> {
            switch (operation) {
                case "get" -> service.get(1L, 999L);
                case "update" -> service.update(1L, 999L, new AssignmentRuleUpdateRequest(AssignmentRuleType.ALL_EMPLOYEES, null, null, null));
                case "status" -> service.changeStatus(1L, 999L, new AssignmentRuleStatusRequest(false));
            }
        });
        verifyNoInteractions(auto);
    }

    @Test
    void deactivateDoesNotAssignAndReactivateReevaluatesEvenIfAlreadyActive() {
        AssignmentRule rule = existing();
        assertThat(service.changeStatus(1L, 100L, new AssignmentRuleStatusRequest(false)).active()).isFalse();
        verifyNoInteractions(auto);
        assertThat(service.changeStatus(1L, 100L, new AssignmentRuleStatusRequest(true)).active()).isTrue();
        service.changeStatus(1L, 100L, new AssignmentRuleStatusRequest(true));
        verify(auto, times(2)).assignRule(1L, 100L);
        assertThat(rule.getId()).isEqualTo(100L);
    }

    @Test
    void activeConditionUpdateEvaluatesNewTargetsAndInactiveUpdateDoesNot() {
        AssignmentRule rule = existing();
        service.update(1L, 100L, new AssignmentRuleUpdateRequest(AssignmentRuleType.NEW_EMPLOYEE, null, null, 30));
        assertThat(rule.getNewEmployeeDays()).isEqualTo((short) 30);
        verify(auto).assignRule(1L, 100L);
        clearInvocations(auto);
        rule.changeActive(false);
        service.update(1L, 100L, new AssignmentRuleUpdateRequest(AssignmentRuleType.ALL_EMPLOYEES, null, null, null));
        assertThat(rule.getNewEmployeeDays()).isNull();
        verifyNoInteractions(auto);
    }

    @Test
    void reactivationRejectsInactiveTargetButDeactivationAlwaysAllowed() {
        lock();
        var rule = rule(course(CourseStatus.OPEN), AssignmentRuleType.DEPARTMENT);
        when(rules.findForUpdate(1L, 100L)).thenReturn(Optional.of(rule));
        var target = department(10L);
        target.deactivate();
        when(departments.findById(10L)).thenReturn(Optional.of(target));
        service.changeStatus(1L, 100L, new AssignmentRuleStatusRequest(false));
        assertCode(ErrorCode.INACTIVE_DEPARTMENT,
                () -> service.changeStatus(1L, 100L, new AssignmentRuleStatusRequest(true)));
        assertThat(rule.isActive()).isFalse();
    }

    @Test
    void readsDtoListAndDetail() {
        var rule = rule(course(CourseStatus.DRAFT), AssignmentRuleType.ALL_EMPLOYEES);
        when(courses.existsById(1L)).thenReturn(true);
        when(rules.findByCourseIdOrderByIdAsc(1L)).thenReturn(List.of(rule));
        when(rules.findByIdAndCourseId(100L, 1L)).thenReturn(Optional.of(rule));
        assertThat(service.getAll(1L)).hasSize(1);
        assertThat(service.get(1L, 100L).courseId()).isEqualTo(1L);
    }

    private void lock() { when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course(CourseStatus.OPEN))); }
    private AssignmentRule existing() {
        lock();
        var value = rule(course(CourseStatus.OPEN), AssignmentRuleType.ALL_EMPLOYEES);
        when(rules.findForUpdate(1L, 100L)).thenReturn(Optional.of(value));
        return value;
    }
    private AssignmentRuleCreateRequest all(boolean active) {
        return new AssignmentRuleCreateRequest(AssignmentRuleType.ALL_EMPLOYEES, null, null, null, active);
    }
    private void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}

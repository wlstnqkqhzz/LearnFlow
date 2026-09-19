package com.be.assignment;

import com.be.assignment.entity.AssignmentRule;
import com.be.assignment.enums.AssignmentRuleType;
import com.be.assignment.repository.AssignmentRuleRepository;
import com.be.assignment.service.AutoAssignmentService;
import com.be.course.entity.Course;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.*;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.member.entity.Member;
import com.be.member.enums.MemberStatus;
import com.be.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static com.be.assignment.AssignmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 엔티티로 상태·날짜 경계·직속 소속·배정 이력 불변 검증
@ExtendWith(MockitoExtension.class)
class AutoAssignmentServiceTest {
    @Mock CourseRepository courses;
    @Mock AssignmentRuleRepository rules;
    @Mock MemberRepository members;
    @Mock EnrollmentRepository enrollments;
    @Mock EntityManager em;
    AutoAssignmentService service;
    final Map<String, Enrollment> stored = new HashMap<>();

    @BeforeEach
    void setUp() { service = new AutoAssignmentService(courses, rules, members, enrollments, CLOCK, em); }

    @ParameterizedTest
    @EnumSource(MemberStatus.class)
    void allEmployeesOnlyAssignsActive(MemberStatus status) {
        var member = member(2L);
        if (status != MemberStatus.ACTIVE) member.changeStatus(status, LocalDateTime.now(CLOCK));
        if (status == MemberStatus.ACTIVE) memberRules(AssignmentRuleType.ALL_EMPLOYEES);
        service.assignMember(member);
        verify(enrollments, times(status == MemberStatus.ACTIVE ? 1 : 0)).saveAndFlush(any());
        if (status != MemberStatus.ACTIVE) verifyNoInteractions(courses, rules, enrollments);
    }

    @ParameterizedTest
    @CsvSource({"0,true", "89,true", "90,false", "-1,false"})
    void newEmployeeBoundariesUseSeoulDate(long days, boolean expected) {
        memberRules(AssignmentRuleType.NEW_EMPLOYEE);
        var member = member(2L);
        member.updateProfile(member.getEmail(), member.getName(), TODAY.minusDays(days));
        service.assignMember(member);
        verify(enrollments, times(expected ? 1 : 0)).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void departmentMatchesDirectMembershipOnly(boolean direct) {
        memberRules(AssignmentRuleType.DEPARTMENT);
        var member = member(2L);
        if (!direct) {
            var child = department(11L);
            child.changeParent(department(10L));
            member.changeDepartment(child);
        }
        service.assignMember(member);
        verify(enrollments, times(direct ? 1 : 0)).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void jobPositionMustMatchExactly(boolean exact) {
        memberRules(AssignmentRuleType.JOB_POSITION);
        var member = member(2L);
        if (!exact) member.changeJobPosition(position(21L));
        service.assignMember(member);
        verify(enrollments, times(exact ? 1 : 0)).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(CourseStatus.class)
    void bulkAssignmentOnlyRunsForOpenCourse(CourseStatus status) {
        var course = course(status);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        if (status == CourseStatus.OPEN) {
            when(rules.findActiveForAssignment(1L)).thenReturn(List.of(rule(course, AssignmentRuleType.ALL_EMPLOYEES)));
            when(members.findAssignmentCandidates(null, null, null, null)).thenReturn(List.of(member(2L)));
        }
        service.assignCourse(1L);
        verify(enrollments, times(status == CourseStatus.OPEN ? 1 : 0)).saveAndFlush(any());
        if (status != CourseStatus.OPEN) verifyNoInteractions(rules, members, enrollments);
    }

    @Test
    void bulkRuleUsesRepositoryDateRangeAndStoresAutomaticFields() {
        var course = course(CourseStatus.OPEN);
        var rule = rule(course, AssignmentRuleType.NEW_EMPLOYEE);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(rules.findForUpdate(1L, 100L)).thenReturn(Optional.of(rule));
        when(members.findAssignmentCandidates(null, null, TODAY.minusDays(89), TODAY)).thenReturn(List.of(member(2L)));
        service.assignRule(1L, 100L);
        var capture = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollments).saveAndFlush(capture.capture());
        var saved = capture.getValue();
        assertThat(saved.getStatus()).isEqualTo(EnrollmentStatus.ASSIGNED);
        assertThat(saved.getAssignmentSource()).isEqualTo(AssignmentSource.AUTOMATIC);
        assertThat(saved.getAssignmentRule()).isSameAs(rule);
        assertThat(saved.getDueDate()).isEqualTo(course.getEndDate());
        assertThat(saved.getAssignedAt()).isEqualTo(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        assertThat(saved.getStartedAt()).isNull();
        assertThat(saved.getCompletedAt()).isNull();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void bulkDefensivelyExcludesLeaveResignedAndNonMatchingCandidates() {
        var course = course(CourseStatus.OPEN);
        var rule = rule(course, AssignmentRuleType.DEPARTMENT);
        var leave = member(3L);
        leave.changeStatus(MemberStatus.ON_LEAVE, LocalDateTime.now(CLOCK));
        var resigned = member(4L);
        resigned.changeStatus(MemberStatus.RESIGNED, LocalDateTime.now(CLOCK));
        var other = member(5L);
        other.changeDepartment(department(11L));
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(rules.findActiveForAssignment(1L)).thenReturn(List.of(rule));
        when(members.findAssignmentCandidates(10L, null, null, null)).thenReturn(List.of(member(2L), leave, resigned, other));
        service.assignCourse(1L);
        verify(enrollments, times(1)).saveAndFlush(any());
    }

    @Test
    void repeatedTriggersAndMultipleRulesKeepFirstEnrollment() {
        useStore();
        var course = course(CourseStatus.OPEN);
        var first = rule(course, AssignmentRuleType.ALL_EMPLOYEES);
        var second = rule(course, AssignmentRuleType.NEW_EMPLOYEE);
        id(second, 101L);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(rules.findActiveForAssignment(1L)).thenReturn(List.of(first, second));
        when(members.findAssignmentCandidates(null, null, null, null)).thenReturn(List.of(member(2L)));
        when(members.findAssignmentCandidates(null, null, TODAY.minusDays(89), TODAY)).thenReturn(List.of(member(2L)));
        service.assignCourse(1L);
        service.assignCourse(1L);
        assertThat(stored).hasSize(1);
        assertThat(stored.get("2:1").getAssignmentRule()).isSameAs(first);
        verify(enrollments, times(1)).saveAndFlush(any());
    }

    @Test
    void existingManualAndTerminalEnrollmentsAreNeverOverwritten() {
        memberRules(AssignmentRuleType.ALL_EMPLOYEES);
        var member = member(2L);
        var manual = Enrollment.manual(member, course(CourseStatus.OPEN), LocalDateTime.now(CLOCK));
        org.springframework.test.util.ReflectionTestUtils.setField(manual, "status", EnrollmentStatus.COMPLETED);
        when(enrollments.findExistingForAssignment(2L, 1L)).thenReturn(Optional.of(manual));
        service.assignMember(member);
        assertThat(manual.getAssignmentSource()).isEqualTo(AssignmentSource.MANUAL);
        assertThat(manual.getAssignmentRule()).isNull();
        assertThat(manual.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        verify(enrollments, never()).saveAndFlush(any());
    }

    @Test
    void leavingConditionAndRuleDeactivationDoNotDeleteHistory() {
        useStore();
        var rule = memberRules(AssignmentRuleType.DEPARTMENT);
        var member = member(2L);
        service.assignMember(member);
        var first = stored.get("2:1");
        member.changeDepartment(department(11L));
        service.assignMember(member);
        rule.changeActive(false);
        service.assignMember(member);
        assertThat(stored.get("2:1")).isSameAs(first);
        assertThat(first.getAssignmentRule()).isSameAs(rule);
        verify(enrollments, never()).delete(any());
        verify(enrollments, times(1)).saveAndFlush(any());
    }

    @Test
    void inactiveRuleDoesNotLoadCandidates() {
        var course = course(CourseStatus.OPEN);
        var rule = rule(course, AssignmentRuleType.ALL_EMPLOYEES);
        rule.changeActive(false);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(rules.findForUpdate(1L, 100L)).thenReturn(Optional.of(rule));
        service.assignRule(1L, 100L);
        verifyNoInteractions(members, enrollments);
    }

    @Test
    void integrityErrorsAreNotSwallowed() {
        memberRules(AssignmentRuleType.ALL_EMPLOYEES);
        var failure = new DataIntegrityViolationException("unexpected FK");
        when(enrollments.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.assignMember(member(2L))).isSameAs(failure);
    }

    private AssignmentRule memberRules(AssignmentRuleType type) {
        Course course = course(CourseStatus.OPEN);
        AssignmentRule rule = rule(course, type);
        when(courses.findOpenForAssignment()).thenReturn(List.of(course));
        when(rules.findActiveForAssignment(1L)).thenReturn(List.of(rule));
        return rule;
    }

    private void useStore() {
        when(enrollments.findExistingForAssignment(anyLong(), anyLong())).thenAnswer(call ->
                Optional.ofNullable(stored.get(call.getArgument(0) + ":" + call.getArgument(1))));
        when(enrollments.saveAndFlush(any())).thenAnswer(call -> {
            Enrollment value = call.getArgument(0);
            stored.put(value.getMember().getId() + ":" + value.getCourse().getId(), value);
            return value;
        });
    }
}

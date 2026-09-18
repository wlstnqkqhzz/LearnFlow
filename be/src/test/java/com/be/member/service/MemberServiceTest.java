package com.be.member.service;

import com.be.global.exception.*;
import com.be.member.dto.*;
import com.be.member.entity.Member;
import com.be.member.enums.*;
import com.be.member.repository.MemberRepository;
import com.be.organization.entity.*;
import com.be.organization.repository.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 회원 Entity와 고정 UTC 시각으로 업무 규칙 검증
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
    @Mock MemberRepository members;
    @Mock DepartmentRepository departments;
    @Mock JobPositionRepository positions;
    @Mock PasswordEncoder encoder;
    MemberService service;
    Department department;
    JobPosition position;
    final LocalDate hireDate = LocalDate.of(2026, 9, 18);
    final LocalDateTime nowUtc = LocalDateTime.of(2026, 9, 18, 1, 0);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-18T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new MemberService(members, departments, positions, encoder, clock);
        department = Department.create("DEV", "개발팀", null);
        position = JobPosition.create("BACKEND", "개발자");
        ReflectionTestUtils.setField(department, "id", 1L);
        ReflectionTestUtils.setField(position, "id", 2L);
    }

    @Test
    void createsWithNormalizedEmailHashAndEmployeeRole() {
        organizationLookup();
        when(encoder.encode("password123!")).thenReturn("encoded-password");
        MemberResponse response = service.create(request());
        ArgumentCaptor<Member> captured = ArgumentCaptor.forClass(Member.class);
        verify(members).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(captured.getValue().getPasswordHash()).isNotEqualTo("password123!");
        assertThat(response.roles()).containsExactly(Role.EMPLOYEE);
        assertThat(response.email()).isEqualTo("kim@example.com");
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.resignedAt()).isNull();
        verify(members).existsByEmail("kim@example.com");
        assertThat(response.toString()).doesNotContain("encoded-password", "password123!");
        assertThat(request().toString()).doesNotContain("password123!");
    }

    @Test
    void rejectsDuplicateEmployeeNumber() {
        when(members.existsByEmployeeNumber("E001")).thenReturn(true);
        assertCode(ErrorCode.DUPLICATE_EMPLOYEE_NUMBER, () -> service.create(request()));
        verifyNoInteractions(encoder);
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        when(members.existsByEmail("kim@example.com")).thenReturn(true);
        assertCode(ErrorCode.DUPLICATE_EMAIL, () -> service.create(request()));
        verifyNoInteractions(encoder);
    }

    @Test
    void rejectsInactiveDepartmentOnCreate() {
        organizationLookup();
        department.deactivate();
        assertCode(ErrorCode.INACTIVE_DEPARTMENT, () -> service.create(request()));
        verifyNoInteractions(encoder);
    }

    @Test
    void rejectsInactivePositionOnCreate() {
        organizationLookup();
        position.deactivate();
        assertCode(ErrorCode.INACTIVE_JOB_POSITION, () -> service.create(request()));
        verifyNoInteractions(encoder);
    }

    @Test
    void activeToLeaveAndBack() {
        Member member = existing();
        assertThat(service.changeStatus(3L, new MemberStatusUpdateRequest(MemberStatus.ON_LEAVE))
                .status()).isEqualTo(MemberStatus.ON_LEAVE);
        assertThat(member.getResignedAt()).isNull();
        assertThat(service.changeStatus(3L, new MemberStatusUpdateRequest(MemberStatus.ACTIVE))
                .status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getResignedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"ACTIVE", "ON_LEAVE"})
    void resignsWithUtcTimestamp(MemberStatus initial) {
        Member member = existing();
        if (initial == MemberStatus.ON_LEAVE) {
            member.changeStatus(initial, nowUtc);
        }
        var response = service.changeStatus(3L, new MemberStatusUpdateRequest(MemberStatus.RESIGNED));
        assertThat(response.status()).isEqualTo(MemberStatus.RESIGNED);
        assertThat(response.resignedAt()).isEqualTo(nowUtc);
    }

    @ParameterizedTest
    @EnumSource(MemberStatus.class)
    void resignedIsTerminal(MemberStatus target) {
        Member member = existing();
        member.changeStatus(MemberStatus.RESIGNED, nowUtc);
        assertCode(ErrorCode.INVALID_MEMBER_STATUS_TRANSITION,
                () -> service.changeStatus(3L, new MemberStatusUpdateRequest(target)));
        assertThat(member.getResignedAt()).isEqualTo(nowUtc);
    }

    @Test
    void rejectsSameStatusTransition() {
        existing();
        assertCode(ErrorCode.INVALID_MEMBER_STATUS_TRANSITION,
                () -> service.changeStatus(3L, new MemberStatusUpdateRequest(MemberStatus.ACTIVE)));
    }

    @Test
    void employeeRoleCannotBeRemoved() {
        Member member = existing();
        assertCode(ErrorCode.REQUIRED_EMPLOYEE_ROLE,
                () -> service.removeRole(3L, new MemberRoleUpdateRequest(Role.EMPLOYEE)));
        assertThat(member.getRoles()).containsExactly(Role.EMPLOYEE);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"INSTRUCTOR", "ADMIN"})
    void addsAndRemovesOptionalRole(Role role) {
        existing();
        var request = new MemberRoleUpdateRequest(role);
        service.addRole(3L, request);
        assertThat(service.addRole(3L, request).roles()).containsExactlyInAnyOrder(Role.EMPLOYEE, role);
        assertThat(service.removeRole(3L, request).roles()).containsExactly(Role.EMPLOYEE);
    }

    @Test
    void rolesCannotBeMutatedThroughResponseOrEntityGetter() {
        Member member = existing(true);
        var response = service.get(3L);
        assertThatThrownBy(() -> response.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> member.getRoles().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updatesProfileWhilePreservingIdentifiersAndInactiveReferences() {
        Member member = existing();
        department.deactivate();
        position.deactivate();
        var response = service.update(3L, new MemberUpdateRequest(" NEW@Example.com ", "새 이름", hireDate));
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.name()).isEqualTo("새 이름");
        assertThat(member.getEmployeeNumber()).isEqualTo("E001");
        assertThat(member.getPasswordHash()).isEqualTo("hash");
        assertThat(member.getDepartment()).isSameAs(department);
        assertThat(member.getJobPosition()).isSameAs(position);
        verify(members, never()).save(any());
    }

    @Test
    void rejectsDuplicateEmailOnUpdate() {
        Member member = existing();
        when(members.existsByEmailAndIdNot("other@example.com", 3L)).thenReturn(true);
        assertCode(ErrorCode.DUPLICATE_EMAIL,
                () -> service.update(3L, new MemberUpdateRequest("OTHER@example.com", "변경", hireDate)));
        assertThat(member.getEmail()).isEqualTo("kim@example.com");
    }

    @Test
    void rejectsResignedProfileAndOrganizationChanges() {
        Member member = existing();
        member.changeStatus(MemberStatus.RESIGNED, nowUtc);
        assertCode(ErrorCode.RESIGNED_MEMBER_UPDATE,
                () -> service.update(3L, new MemberUpdateRequest("new@example.com", "변경", hireDate)));
        assertCode(ErrorCode.RESIGNED_MEMBER_UPDATE,
                () -> service.changeDepartment(3L, new MemberDepartmentUpdateRequest(10L)));
        assertCode(ErrorCode.RESIGNED_MEMBER_UPDATE,
                () -> service.changeJobPosition(3L, new MemberJobPositionUpdateRequest(20L)));
        verifyNoInteractions(departments, positions);
    }

    @Test
    void changesDepartmentAndPosition() {
        Member member = existing();
        Department nextDepartment = Department.create("NEXT", "이동 부서", null);
        JobPosition nextPosition = JobPosition.create("NEXT", "이동 직무");
        ReflectionTestUtils.setField(nextDepartment, "id", 10L);
        ReflectionTestUtils.setField(nextPosition, "id", 20L);
        when(departments.findById(10L)).thenReturn(Optional.of(nextDepartment));
        when(positions.findById(20L)).thenReturn(Optional.of(nextPosition));
        assertThat(service.changeDepartment(3L, new MemberDepartmentUpdateRequest(10L)).departmentId())
                .isEqualTo(10L);
        assertThat(service.changeJobPosition(3L, new MemberJobPositionUpdateRequest(20L)).jobPositionId())
                .isEqualTo(20L);
        assertThat(member.getDepartment()).isSameAs(nextDepartment);
        assertThat(member.getJobPosition()).isSameAs(nextPosition);
    }

    @Test
    void rejectsInactiveOrganizationChanges() {
        existing();
        Department inactive = Department.create("OFF", "비활성", null);
        JobPosition inactivePosition = JobPosition.create("OFF", "비활성");
        inactive.deactivate();
        inactivePosition.deactivate();
        when(departments.findById(10L)).thenReturn(Optional.of(inactive));
        when(positions.findById(20L)).thenReturn(Optional.of(inactivePosition));
        assertCode(ErrorCode.INACTIVE_DEPARTMENT,
                () -> service.changeDepartment(3L, new MemberDepartmentUpdateRequest(10L)));
        assertCode(ErrorCode.INACTIVE_JOB_POSITION,
                () -> service.changeJobPosition(3L, new MemberJobPositionUpdateRequest(20L)));
    }

    @Test
    void rejectsMissingMember() {
        assertCode(ErrorCode.MEMBER_NOT_FOUND, () -> service.get(99L));
    }

    @Test
    void rejectsMissingDepartment() {
        assertCode(ErrorCode.DEPARTMENT_NOT_FOUND, () -> service.create(request()));
    }

    @Test
    void rejectsMissingJobPosition() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        assertCode(ErrorCode.JOB_POSITION_NOT_FOUND, () -> service.create(request()));
    }

    private MemberCreateRequest request() {
        return new MemberCreateRequest("E001", "  KIM@Example.COM  ", "password123!", "김개발",
                1L, 2L, hireDate);
    }

    private void organizationLookup() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(positions.findById(2L)).thenReturn(Optional.of(position));
    }

    private Member existing() {
        return existing(false);
    }

    private Member existing(boolean readOnly) {
        Member member = Member.create("E001", "kim@example.com", "hash", "김개발",
                department, position, hireDate);
        ReflectionTestUtils.setField(member, "id", 3L);
        if (readOnly) {
            when(members.findById(3L)).thenReturn(Optional.of(member));
        } else {
            when(members.findByIdForUpdate(3L)).thenReturn(Optional.of(member));
        }
        return member;
    }

    private void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}

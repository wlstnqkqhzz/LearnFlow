package com.be.assignment;

import com.be.assignment.service.AutoAssignmentService;
import com.be.course.dto.CourseStatusRequest;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.course.service.CourseService;
import com.be.member.dto.*;
import com.be.member.enums.*;
import com.be.member.repository.MemberRepository;
import com.be.member.service.MemberService;
import com.be.organization.repository.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static com.be.assignment.AssignmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 기존 Service의 확정된 변경 지점만 자동 배정을 호출하고 실패는 호출자에게 전파
@ExtendWith(MockitoExtension.class)
class AssignmentTriggerTest {
    @Mock MemberRepository members;
    @Mock DepartmentRepository departments;
    @Mock JobPositionRepository positions;
    @Mock PasswordEncoder encoder;
    @Mock AutoAssignmentService auto;
    @Mock CourseRepository courses;
    MemberService memberService;
    CourseService courseService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(members, departments, positions, encoder, CLOCK, auto);
        courseService = new CourseService(courses, members, auto);
    }

    @Test
    void creationTriggersOnlyAfterPersistence() {
        when(departments.findById(10L)).thenReturn(Optional.of(department(10L)));
        when(positions.findById(20L)).thenReturn(Optional.of(position(20L)));
        when(encoder.encode("password123!")).thenReturn("hash");
        doAnswer(call -> { id(call.getArgument(0), 2L); return call.getArgument(0); }).when(members).saveAndFlush(any());
        memberService.create(new MemberCreateRequest("E2", "user@example.com", "password123!", "직원", 10L, 20L, TODAY));
        InOrder order = inOrder(members, auto);
        order.verify(members).saveAndFlush(any());
        order.verify(auto).assignMember(argThat(member -> member.getId().equals(2L)));
    }

    @Test
    void departmentAndJobChangeTriggerAfterFlushAndAreRepeatable() {
        var member = member(2L);
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));
        when(departments.findById(11L)).thenReturn(Optional.of(department(11L)));
        when(positions.findById(21L)).thenReturn(Optional.of(position(21L)));
        memberService.changeDepartment(2L, new MemberDepartmentUpdateRequest(11L));
        memberService.changeDepartment(2L, new MemberDepartmentUpdateRequest(11L));
        memberService.changeJobPosition(2L, new MemberJobPositionUpdateRequest(21L));
        verify(auto, times(3)).assignMember(member);
        assertThat(member.getDepartment().getId()).isEqualTo(11L);
        assertThat(member.getJobPosition().getId()).isEqualTo(21L);
    }

    @Test
    void hireDateTriggersInBothUpdatePathsButNameOnlyDoesNot() {
        var member = member(2L);
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));
        var nameOnly = new MemberPatchRequest();
        nameOnly.setName("새 이름");
        memberService.patch(2L, nameOnly);
        verifyNoInteractions(auto);
        var patch = new MemberPatchRequest();
        patch.setHireDate(TODAY.minusDays(1));
        memberService.patch(2L, patch);
        memberService.update(2L, new MemberUpdateRequest(member.getEmail(), member.getName(), TODAY));
        verify(auto, times(2)).assignMember(member);
    }

    @Test
    void statusAndRoleChangesDoNotTriggerAssignment() {
        var member = member(2L);
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));
        memberService.changeStatus(2L, new MemberStatusUpdateRequest(MemberStatus.ON_LEAVE));
        memberService.changeStatus(2L, new MemberStatusUpdateRequest(MemberStatus.ACTIVE));
        memberService.addRole(2L, new MemberRoleUpdateRequest(Role.INSTRUCTOR));
        memberService.removeRole(2L, new MemberRoleUpdateRequest(Role.INSTRUCTOR));
        verifyNoInteractions(auto);
    }

    @Test
    void courseOpenTriggersAfterStatusFlushButCloseDoesNot() {
        var course = course(CourseStatus.DRAFT);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        courseService.changeStatus(1L, new CourseStatusRequest(CourseStatus.OPEN));
        InOrder order = inOrder(courses, auto);
        order.verify(courses).flush();
        order.verify(auto).assignCourse(1L);
        clearInvocations(auto);
        courseService.changeStatus(1L, new CourseStatusRequest(CourseStatus.CLOSED));
        verifyNoInteractions(auto);
    }

    @Test
    void assignmentFailureIsNotSilentlyCommittedByCaller() {
        var course = course(CourseStatus.DRAFT);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        var failure = new org.springframework.dao.CannotAcquireLockException("conflict");
        doThrow(failure).when(auto).assignCourse(1L);
        assertThatThrownBy(() -> courseService.changeStatus(1L, new CourseStatusRequest(CourseStatus.OPEN))).isSameAs(failure);
        // 실제 롤백은 Spring 트랜잭션 경계에서 수행하며 이 테스트는 예외 전파를 확인
    }
}

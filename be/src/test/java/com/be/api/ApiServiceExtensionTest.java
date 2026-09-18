package com.be.api;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.member.dto.*;
import com.be.member.entity.Member;
import com.be.member.enums.MemberStatus;
import com.be.member.repository.MemberRepository;
import com.be.member.service.MemberService;
import com.be.organization.dto.DepartmentPatchRequest;
import com.be.organization.entity.*;
import com.be.organization.repository.*;
import com.be.organization.service.DepartmentService;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// API 연결을 위해 추가한 부분 수정과 검색의 데이터 보존 검증
class ApiServiceExtensionTest {
    MemberRepository members;
    DepartmentRepository departments;
    MemberService service;
    DepartmentService departmentService;
    Department parent;
    Department child;
    Member member;

    @BeforeEach
    void setUp() {
        members = mock(MemberRepository.class);
        departments = mock(DepartmentRepository.class);
        service = new MemberService(members, departments, mock(JobPositionRepository.class),
                mock(PasswordEncoder.class), Clock.systemUTC());
        departmentService = new DepartmentService(departments);
        parent = Department.create("ROOT", "상위 부서", null);
        child = Department.create("DEV", "개발팀", parent);
        ReflectionTestUtils.setField(parent, "id", 1L);
        ReflectionTestUtils.setField(child, "id", 2L);
        JobPosition position = JobPosition.create("DEV", "개발자");
        ReflectionTestUtils.setField(position, "id", 3L);
        member = Member.create("E001", "kim@example.com", "hash", "김개발",
                child, position, LocalDate.of(2026, 9, 18));
        ReflectionTestUtils.setField(member, "id", 4L);
        when(members.findByIdForUpdate(4L)).thenReturn(Optional.of(member));
        when(departments.findAllForUpdate()).thenReturn(List.of(parent, child));
    }

    @Test
    void memberNamePatchPreservesOtherFields() {
        var request = new MemberPatchRequest();
        request.setName("새 이름");
        var response = service.patch(4L, request);
        assertThat(response.name()).isEqualTo("새 이름");
        assertThat(response.email()).isEqualTo("kim@example.com");
        assertThat(response.hireDate()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(member.getPasswordHash()).isEqualTo("hash");
        assertThat(member.getDepartment()).isSameAs(child);
    }

    @Test
    void duplicatePatchEmailFailsBeforeMutation() {
        when(members.existsByEmailAndIdNot("other@example.com", 4L)).thenReturn(true);
        var request = new MemberPatchRequest();
        request.setEmail(" OTHER@Example.com ");
        assertThatThrownBy(() -> service.patch(4L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
        assertThat(member.getEmail()).isEqualTo("kim@example.com");
    }

    @Test
    void resignedMemberCannotBePatched() {
        member.changeStatus(MemberStatus.RESIGNED, LocalDateTime.now(ZoneOffset.UTC));
        var request = new MemberPatchRequest();
        request.setName("변경");
        assertThatThrownBy(() -> service.patch(4L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESIGNED_MEMBER_UPDATE));
    }

    @Test
    void missingParentPreservesExistingInactiveParent() {
        parent.deactivate();
        var request = new DepartmentPatchRequest();
        request.setName("새 부서명");
        var response = departmentService.patch(2L, request);
        assertThat(response.name()).isEqualTo("새 부서명");
        assertThat(child.getParentDepartment()).isSameAs(parent);
        assertThat(child.getCode()).isEqualTo("DEV");
    }

    @Test
    void explicitNullParentDetachesWithoutChangingName() {
        var request = new DepartmentPatchRequest();
        request.setParentDepartmentId(null);
        var response = departmentService.patch(2L, request);
        assertThat(response.parentDepartmentId()).isNull();
        assertThat(response.name()).isEqualTo("개발팀");
    }

    @Test
    void searchPassesEscapedLiteralNameAndPagination() {
        Pageable pageable = PageRequest.of(1, 5, Sort.by("id"));
        when(members.search(eq("%김!_!%!!%"), eq(2L), eq(3L), eq(MemberStatus.ACTIVE), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(member), pageable, 20));
        var result = service.search(new MemberSearchRequest(1, 5, " 김_%! ", 2L, 3L, MemberStatus.ACTIVE));
        assertThat(result.getTotalElements()).isEqualTo(20);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(4L);
    }

    @Test
    void searchWithoutFiltersUsesDefaultPage() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("id"));
        when(members.search(null, null, null, null, pageable)).thenReturn(Page.empty(pageable));
        var result = service.search(new MemberSearchRequest(null, null, " ", null, null, null));
        assertThat(result.getContent()).isEmpty();
        verify(members).search(null, null, null, null, pageable);
    }
}

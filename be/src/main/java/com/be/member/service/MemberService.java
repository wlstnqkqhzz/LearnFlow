package com.be.member.service;

import com.be.assignment.service.AutoAssignmentService;
import com.be.global.exception.*;
import com.be.member.dto.*;
import com.be.member.entity.Member;
import com.be.member.repository.MemberRepository;
import com.be.organization.entity.Department;
import com.be.organization.entity.JobPosition;
import com.be.organization.repository.DepartmentRepository;
import com.be.organization.repository.JobPositionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 직원 등록, 정보·상태·역할 변경 관리 및 지정된 변경의 자동 배정 연동
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final DepartmentRepository departmentRepository;
    private final JobPositionRepository jobPositionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    // 생성·소속·입사일 변경만 평가하며 상태/역할 변경에는 연결하지 않음
    private final AutoAssignmentService autoAssignment;

    @Transactional
    public MemberResponse create(@NotNull @Valid MemberCreateRequest request) {
        if (memberRepository.existsByEmployeeNumber(request.employeeNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMPLOYEE_NUMBER);
        }
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        Department department = findDepartment(request.departmentId());
        JobPosition position = findJobPosition(request.jobPositionId());
        if (!department.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_DEPARTMENT);
        }
        if (!position.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_JOB_POSITION);
        }
        Member member = Member.create(request.employeeNumber(), request.email(),
                passwordEncoder.encode(request.password()), request.name(), department, position, request.hireDate());
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw UniqueConstraintErrors.translate(exception);
        }
        // 생성된 ID 및 최신 소속으로 평가; 실패하면 회원 생성도 롤백
        autoAssignment.assignMember(member);
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse update(@NotNull @Positive Long id,
                                 @NotNull @Valid MemberUpdateRequest request) {
        Member member = findForUpdate(id);
        member.ensureEditable();
        if (memberRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        boolean hireDateChanged = !member.getHireDate().equals(request.hireDate());
        member.updateProfile(request.email(), request.name(), request.hireDate());
        MemberResponse response = flushAndRespond(member);
        if (hireDateChanged) autoAssignment.assignMember(member);
        return response;
    }

    @Transactional
    public MemberResponse changeDepartment(@NotNull @Positive Long id,
                                           @NotNull @Valid MemberDepartmentUpdateRequest request) {
        Member member = findForUpdate(id);
        member.ensureEditable();
        member.changeDepartment(findDepartment(request.departmentId()));
        MemberResponse response = flushAndRespond(member);
        autoAssignment.assignMember(member);
        return response;
    }

    @Transactional
    public MemberResponse changeJobPosition(@NotNull @Positive Long id,
                                            @NotNull @Valid MemberJobPositionUpdateRequest request) {
        Member member = findForUpdate(id);
        member.ensureEditable();
        member.changeJobPosition(findJobPosition(request.jobPositionId()));
        MemberResponse response = flushAndRespond(member);
        autoAssignment.assignMember(member);
        return response;
    }

    @Transactional
    public MemberResponse changeStatus(@NotNull @Positive Long id,
                                       @NotNull @Valid MemberStatusUpdateRequest request) {
        Member member = findForUpdate(id);
        member.changeStatus(request.status(), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return flushAndRespond(member);
    }

    @Transactional
    public MemberResponse addRole(@NotNull @Positive Long id,
                                  @NotNull @Valid MemberRoleUpdateRequest request) {
        Member member = findForUpdate(id);
        member.addRole(request.role());
        return flushAndRespond(member);
    }

    @Transactional
    public MemberResponse removeRole(@NotNull @Positive Long id,
                                     @NotNull @Valid MemberRoleUpdateRequest request) {
        Member member = findForUpdate(id);
        member.removeRole(request.role());
        return flushAndRespond(member);
    }

    public MemberResponse get(@NotNull @Positive Long id) {
        return MemberResponse.from(find(id));
    }

    // 부분 수정은 잠긴 최신 회원 정보와 병합하여 생략 필드를 유지
    @Transactional
    public MemberResponse patch(@NotNull @Positive Long id,
                                @NotNull @Valid MemberPatchRequest request) {
        Member member = findForUpdate(id);
        member.ensureEditable();
        String email = request.getEmail() == null ? member.getEmail() : request.getEmail();
        if (memberRepository.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        boolean hireDateChanged = request.getHireDate() != null && !request.getHireDate().equals(member.getHireDate());
        member.updateProfile(email,
                request.getName() == null ? member.getName() : request.getName(),
                request.getHireDate() == null ? member.getHireDate() : request.getHireDate());
        MemberResponse response = flushAndRespond(member);
        if (hireDateChanged) autoAssignment.assignMember(member);
        return response;
    }

    // 단순 조건 검색과 안정적인 ID 정렬로 직원 페이지 조회
    public Page<MemberResponse> search(@NotNull @Valid MemberSearchRequest request) {
        String pattern = request.name() == null ? null
                : "%" + request.name().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var page = memberRepository.search(pattern, request.departmentId(), request.jobPositionId(), request.status(),
                PageRequest.of(request.page(), request.size(), Sort.by("id")));
        // 같은 영속성 컨텍스트의 역할을 한 번에 초기화하여 회원별 추가 SELECT 방지
        if (!page.isEmpty()) memberRepository.findWithRolesByIdIn(page.getContent().stream().map(Member::getId).toList());
        return page.map(MemberResponse::from);
    }

    private Member find(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    // 변경 작업은 회원 행 잠금을 획득한 최신 상태를 기준으로 검증
    private Member findForUpdate(Long id) {
        return memberRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    private JobPosition findJobPosition(Long id) {
        return jobPositionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSITION_NOT_FOUND));
    }

    // Dirty Checking을 반영하고 감사 시각을 갱신한 뒤 응답 생성
    private MemberResponse flushAndRespond(Member member) {
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw UniqueConstraintErrors.translate(exception);
        }
        return MemberResponse.from(member);
    }
}

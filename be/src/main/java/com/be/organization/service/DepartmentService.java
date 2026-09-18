package com.be.organization.service;

import com.be.global.exception.*;
import com.be.organization.dto.*;
import com.be.organization.entity.Department;
import com.be.organization.repository.DepartmentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 부서 코드 중복, 계층 변경 및 활성 상태 관리
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {
    private final DepartmentRepository departmentRepository;

    @Transactional
    public DepartmentResponse create(@NotNull @Valid DepartmentCreateRequest request) {
        List<Department> departments = departmentRepository.findAllForUpdate();
        if (departmentRepository.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_DEPARTMENT_CODE);
        }
        Department parent = request.parentDepartmentId() == null ? null
                : findInLockedHierarchy(departments, request.parentDepartmentId());
        Department department = Department.create(request.code(), request.name(), parent);
        try {
            departmentRepository.saveAndFlush(department);
        } catch (DataIntegrityViolationException exception) {
            throw UniqueConstraintErrors.translate(exception);
        }
        return DepartmentResponse.from(department);
    }

    @Transactional
    public DepartmentResponse update(@NotNull @Positive Long id,
                                     @NotNull @Valid DepartmentUpdateRequest request) {
        List<Department> departments = departmentRepository.findAllForUpdate();
        Department department = findInLockedHierarchy(departments, id);
        Department parent = request.parentDepartmentId() == null ? null
                : findInLockedHierarchy(departments, request.parentDepartmentId());
        department.changeParent(parent);
        department.rename(request.name());
        departmentRepository.flush();
        return DepartmentResponse.from(department);
    }

    @Transactional
    public DepartmentResponse activate(@NotNull @Positive Long id) {
        Department department = findInLockedHierarchy(departmentRepository.findAllForUpdate(), id);
        department.activate();
        departmentRepository.flush();
        return DepartmentResponse.from(department);
    }

    @Transactional
    public DepartmentResponse deactivate(@NotNull @Positive Long id) {
        Department department = findInLockedHierarchy(departmentRepository.findAllForUpdate(), id);
        department.deactivate();
        departmentRepository.flush();
        return DepartmentResponse.from(department);
    }

    public DepartmentResponse get(@NotNull @Positive Long id) {
        return DepartmentResponse.from(departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND)));
    }

    public List<DepartmentResponse> getAll() {
        return departmentRepository.findAll(Sort.by("id")).stream()
                .map(DepartmentResponse::from).toList();
    }

    public List<DepartmentResponse> getActive() {
        return departmentRepository.findByIsActiveTrue().stream()
                .map(DepartmentResponse::from).toList();
    }

    // 잠긴 동일 영속성 컨텍스트의 계층에서 조회하여 상위 부서를 검증
    private Department findInLockedHierarchy(List<Department> departments, Long id) {
        return departments.stream().filter(department -> id.equals(department.getId()))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }
}

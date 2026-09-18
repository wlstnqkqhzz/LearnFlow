package com.be.organization.service;

import com.be.global.exception.*;
import com.be.organization.dto.*;
import com.be.organization.entity.Department;
import com.be.organization.repository.DepartmentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 부서 객체로 계층과 활성 상태 규칙 검증
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {
    @Mock DepartmentRepository repository;
    DepartmentService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentService(repository);
    }

    @Test
    void createsDepartment() {
        when(repository.findAllForUpdate()).thenReturn(List.of());
        var response = service.create(new DepartmentCreateRequest(" DEV ", " 개발팀 ", null));
        assertThat(response.code()).isEqualTo("DEV");
        assertThat(response.name()).isEqualTo("개발팀");
        assertThat(response.isActive()).isTrue();
        assertThat(response.parentDepartmentId()).isNull();
        verify(repository).saveAndFlush(any(Department.class));
    }

    @Test
    void rejectsDuplicateCode() {
        when(repository.existsByCode("DEV")).thenReturn(true);
        assertCode(ErrorCode.DUPLICATE_DEPARTMENT_CODE,
                () -> service.create(new DepartmentCreateRequest("DEV", "개발팀", null)));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createsUnderActiveParent() {
        Department parent = department(1L, null);
        when(repository.findAllForUpdate()).thenReturn(List.of(parent));
        assertThat(service.create(new DepartmentCreateRequest("CHILD", "하위", 1L))
                .parentDepartmentId()).isEqualTo(1L);
    }

    @Test
    void rejectsSelfParent() {
        Department department = department(1L, null);
        when(repository.findAllForUpdate()).thenReturn(List.of(department));
        assertCode(ErrorCode.SELF_PARENT_DEPARTMENT,
                () -> service.update(1L, new DepartmentUpdateRequest("새 이름", 1L)));
        assertThat(department.getName()).isEqualTo("부서1");
    }

    @Test
    void rejectsDescendantAsParent() {
        Department root = department(1L, null);
        Department child = department(2L, root);
        Department grandchild = department(3L, child);
        when(repository.findAllForUpdate()).thenReturn(List.of(root, child, grandchild));
        assertCode(ErrorCode.DEPARTMENT_CYCLE,
                () -> service.update(1L, new DepartmentUpdateRequest("순환", 3L)));
        assertThat(root.getParentDepartment()).isNull();
    }

    @Test
    void rejectsInactiveParentOnCreate() {
        Department parent = department(1L, null);
        parent.deactivate();
        when(repository.findAllForUpdate()).thenReturn(List.of(parent));
        assertCode(ErrorCode.INACTIVE_DEPARTMENT,
                () -> service.create(new DepartmentCreateRequest("CHILD", "하위", 1L)));
    }

    @Test
    void rejectsInactiveNewParentOnUpdate() {
        Department department = department(1L, null);
        Department parent = department(2L, null);
        parent.deactivate();
        when(repository.findAllForUpdate()).thenReturn(List.of(department, parent));
        assertCode(ErrorCode.INACTIVE_DEPARTMENT,
                () -> service.update(1L, new DepartmentUpdateRequest("변경", 2L)));
    }

    @Test
    void preservesExistingInactiveParentAndImmutableCode() {
        Department parent = department(1L, null);
        Department child = department(2L, parent);
        parent.deactivate();
        when(repository.findAllForUpdate()).thenReturn(List.of(parent, child));
        var response = service.update(2L, new DepartmentUpdateRequest("이름 수정", 1L));
        assertThat(response.name()).isEqualTo("이름 수정");
        assertThat(response.code()).isEqualTo("D2");
        assertThat(child.getParentDepartment()).isSameAs(parent);
        verify(repository, never()).save(any());
    }

    @Test
    void removesParent() {
        Department parent = department(1L, null);
        Department child = department(2L, parent);
        when(repository.findAllForUpdate()).thenReturn(List.of(parent, child));
        assertThat(service.update(2L, new DepartmentUpdateRequest("최상위", null))
                .parentDepartmentId()).isNull();
    }

    @Test
    void activationDoesNotCascadeToChildren() {
        Department parent = department(1L, null);
        Department child = department(2L, parent);
        when(repository.findAllForUpdate()).thenReturn(List.of(parent, child));
        assertThat(service.deactivate(1L).isActive()).isFalse();
        assertThat(child.isActive()).isTrue();
        assertThat(child.getParentDepartment()).isSameAs(parent);
        assertThat(service.activate(1L).isActive()).isTrue();
    }

    @Test
    void readsDtoAndActiveList() {
        Department department = department(1L, null);
        when(repository.findById(1L)).thenReturn(Optional.of(department));
        when(repository.findByIsActiveTrue()).thenReturn(List.of(department));
        assertThat(service.get(1L).id()).isEqualTo(1L);
        assertThat(service.getActive()).hasSize(1);
    }

    @Test
    void rejectsMissingParent() {
        assertCode(ErrorCode.DEPARTMENT_NOT_FOUND,
                () -> service.create(new DepartmentCreateRequest("DEV", "개발", 99L)));
    }

    @Test
    void rejectsMissingDepartment() {
        assertCode(ErrorCode.DEPARTMENT_NOT_FOUND, () -> service.get(99L));
    }

    private Department department(Long id, Department parent) {
        Department department = Department.create("D" + id, "부서" + id, parent);
        ReflectionTestUtils.setField(department, "id", id);
        return department;
    }

    private void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}

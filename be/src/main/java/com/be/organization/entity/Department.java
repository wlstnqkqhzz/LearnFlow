package com.be.organization.entity;

import com.be.global.entity.BaseTimeEntity;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

// 부서 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "departments",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_departments_code", columnNames = {"code"})
        },
        indexes = {
                @Index(name = "idx_departments_parent_name", columnList = "parent_department_id, name")
        },
        check = {
                @CheckConstraint(name = "chk_departments_is_active",
                        constraint = "is_active IN (0, 1)")
        })
public class Department extends BaseTimeEntity {

    // 부서 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 부서 식별 코드 (중복 불가)
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    // 부서명
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 상위 부서 (최상위 부서는 null)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "parent_department_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_departments_parent", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Department parentDepartment;

    // 활성 여부 - 물리 삭제 대신 비활성화에 사용
    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false, columnDefinition = "boolean")
    private boolean isActive = true;

    // 부서 생성 - 코드는 이후 변경하지 않음
    public static Department create(String code, String name, Department parent) {
        Department department = new Department();
        department.code = code.trim();
        department.rename(name);
        department.changeParent(parent);
        return department;
    }

    // 부서명 변경
    public void rename(String name) {
        this.name = name.trim();
    }

    // 기존 비활성 상위 부서는 유지할 수 있지만 새로 지정할 수는 없음
    public void changeParent(Department parent) {
        if (parent == this || (parent != null && id != null && id.equals(parent.getId()))) {
            throw new BusinessException(ErrorCode.SELF_PARENT_DEPARTMENT);
        }
        if (parent == parentDepartment || (parent != null && parentDepartment != null
                && parent.getId() != null && parent.getId().equals(parentDepartment.getId()))) {
            return;
        }
        if (parent != null && !parent.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_DEPARTMENT);
        }
        Set<Department> visited = new HashSet<>();
        Department cursor = parent;
        while (cursor != null) {
            if (cursor == this || (id != null && id.equals(cursor.getId())) || !visited.add(cursor)) {
                throw new BusinessException(ErrorCode.DEPARTMENT_CYCLE);
            }
            cursor = cursor.getParentDepartment();
        }
        parentDepartment = parent;
    }

    // 하위 부서나 기존 회원 참조는 변경하지 않음
    public void activate() {
        isActive = true;
    }

    public void deactivate() {
        isActive = false;
    }
}

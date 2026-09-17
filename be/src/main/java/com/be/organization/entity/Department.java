package com.be.organization.entity;

import com.be.global.entity.BaseTimeEntity;
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
}

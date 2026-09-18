package com.be.member.entity;

import com.be.global.entity.BaseTimeEntity;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.member.enums.MemberStatus;
import com.be.member.enums.Role;
import com.be.organization.entity.Department;
import com.be.organization.entity.JobPosition;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 회원 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "members",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_members_employee_number", columnNames = {"employee_number"}),
                @UniqueConstraint(name = "uk_members_email", columnNames = {"email"})
        },
        indexes = {
                @Index(name = "idx_members_department_status", columnList = "department_id, status"),
                @Index(name = "idx_members_job_position_status", columnList = "job_position_id, status"),
                @Index(name = "idx_members_status_hire_date", columnList = "status, hire_date")
        },
        check = {
                @CheckConstraint(name = "chk_members_status_resigned_at",
                        constraint = "(status IN ('ACTIVE', 'ON_LEAVE') AND resigned_at IS NULL) OR (status = 'RESIGNED' AND resigned_at IS NOT NULL)"),
                @CheckConstraint(name = "chk_members_status",
                        constraint = "CAST(status AS BINARY) IN ('ACTIVE', 'ON_LEAVE', 'RESIGNED')")
        })
public class Member extends BaseTimeEntity {

    // 회원 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 사번 (중복 불가)
    @Column(name = "employee_number", nullable = false, length = 50)
    private String employeeNumber;

    // 로그인 이메일 (중복 불가)
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    // 해시 처리된 비밀번호
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // 회원 이름
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 소속 부서
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_members_department", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Department department;

    // 직무
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_position_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_members_job_position", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private JobPosition jobPosition;

    // 회원 상태 (재직 / 휴직 / 퇴사)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    // 입사일
    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    // 퇴사 시각 (UTC, 퇴사 상태에서만 존재)
    @Column(name = "resigned_at", nullable = true, columnDefinition = "datetime(6)")
    private LocalDateTime resignedAt;

    // 회원 역할 목록 - member_roles 연결 테이블에 저장
    @Getter(AccessLevel.NONE)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_roles",
            joinColumns = @JoinColumn(name = "member_id", nullable = false),
            foreignKey = @ForeignKey(name = "fk_member_roles_member",
                    options = "ON DELETE RESTRICT ON UPDATE RESTRICT"),
            indexes = @Index(name = "idx_member_roles_role_member", columnList = "role, member_id"),
            options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 20,
            check = @CheckConstraint(name = "chk_member_roles_role",
                    constraint = "CAST(role AS BINARY) IN ('EMPLOYEE', 'INSTRUCTOR', 'ADMIN')"))
    private Set<Role> roles = new HashSet<>(Set.of(Role.EMPLOYEE));

    // 외부에서 역할 목록을 직접 수정하지 못하도록 읽기 전용 뷰 반환
    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    // 해시 처리된 비밀번호로만 회원 생성 - EMPLOYEE 역할은 필드 기본값으로 유지
    public static Member create(String employeeNumber, String email, String passwordHash,
                                String name, Department department, JobPosition jobPosition,
                                LocalDate hireDate) {
        Member member = new Member();
        member.employeeNumber = employeeNumber.trim();
        member.passwordHash = Objects.requireNonNull(passwordHash);
        member.updateProfile(email, name, hireDate);
        member.changeDepartment(department);
        member.changeJobPosition(jobPosition);
        return member;
    }

    // 이메일은 검증 및 저장 전에 동일한 방식으로 정규화
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // 사번과 비밀번호를 제외한 일반 정보 변경
    public void updateProfile(String email, String name, LocalDate hireDate) {
        ensureEditable();
        this.email = normalizeEmail(email);
        this.name = name.trim();
        this.hireDate = Objects.requireNonNull(hireDate);
    }

    // 신규 소속 지정은 활성 부서만 허용
    public void changeDepartment(Department department) {
        ensureEditable();
        Objects.requireNonNull(department);
        if (this.department != null && (this.department == department
                || (department.getId() != null && department.getId().equals(this.department.getId())))) {
            return;
        }
        if (!department.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_DEPARTMENT);
        }
        this.department = department;
    }

    // 신규 직무 지정은 활성 직무만 허용
    public void changeJobPosition(JobPosition jobPosition) {
        ensureEditable();
        Objects.requireNonNull(jobPosition);
        if (this.jobPosition != null && (this.jobPosition == jobPosition
                || (jobPosition.getId() != null && jobPosition.getId().equals(this.jobPosition.getId())))) {
            return;
        }
        if (!jobPosition.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_JOB_POSITION);
        }
        this.jobPosition = jobPosition;
    }

    // 퇴사자는 일반 정보를 수정할 수 없음
    public void ensureEditable() {
        if (status == MemberStatus.RESIGNED) {
            throw new BusinessException(ErrorCode.RESIGNED_MEMBER_UPDATE);
        }
    }

    // 허용된 재직 상태 전이만 적용하고 퇴사 시각은 UTC로 전달받음
    public void changeStatus(MemberStatus nextStatus, LocalDateTime nowUtc) {
        Objects.requireNonNull(nextStatus);
        if (status == MemberStatus.RESIGNED || status == nextStatus) {
            throw new BusinessException(ErrorCode.INVALID_MEMBER_STATUS_TRANSITION);
        }
        LocalDateTime nextResignedAt = nextStatus == MemberStatus.RESIGNED
                ? Objects.requireNonNull(nowUtc) : null;
        status = nextStatus;
        resignedAt = nextResignedAt;
    }

    // 추가 역할은 Set으로 중복 방지
    public void addRole(Role role) {
        roles.add(Objects.requireNonNull(role));
    }

    // 기본 직원 역할은 항상 유지
    public void removeRole(Role role) {
        if (Objects.requireNonNull(role) == Role.EMPLOYEE) {
            throw new BusinessException(ErrorCode.REQUIRED_EMPLOYEE_ROLE);
        }
        roles.remove(role);
    }
}

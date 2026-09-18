package com.be.organization.entity;

import com.be.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

// 직무 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "job_positions",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_job_positions_code", columnNames = {"code"})
        },
        check = {
                @CheckConstraint(name = "chk_job_positions_is_active",
                        constraint = "is_active IN (0, 1)")
        })
public class JobPosition extends BaseTimeEntity {

    // 직무 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 직무 식별 코드 (중복 불가)
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    // 직무명
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 활성 여부 - 물리 삭제 대신 비활성화에 사용
    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false, columnDefinition = "boolean")
    private boolean isActive = true;

    // 직무 생성 - 코드는 이후 변경하지 않음
    public static JobPosition create(String code, String name) {
        JobPosition jobPosition = new JobPosition();
        jobPosition.code = code.trim();
        jobPosition.rename(name);
        return jobPosition;
    }

    // 직무명 변경
    public void rename(String name) {
        this.name = name.trim();
    }

    public void activate() {
        isActive = true;
    }

    // 기존 회원의 직무 참조는 유지
    public void deactivate() {
        isActive = false;
    }
}

package com.be.organization.repository;

import com.be.organization.entity.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

// 부서 코드 중복 검사 및 활성 목록 조회
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByCode(String code);

    List<Department> findByIsActiveTrue();

    // 계층 변경을 직렬화하여 동시 상위 부서 변경에 의한 순환을 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d order by d.id")
    List<Department> findAllForUpdate();

}

package com.be.organization.repository;

import com.be.organization.entity.JobPosition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;


// 직무 코드 중복 검사 및 활성 목록 조회
public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {
    boolean existsByCode(String code);

    List<JobPosition> findByIsActiveTrue();

}

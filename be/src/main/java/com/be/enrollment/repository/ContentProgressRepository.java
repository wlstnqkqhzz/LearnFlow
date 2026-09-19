package com.be.enrollment.repository;

import com.be.enrollment.entity.ContentProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

// 수강 단위 진도 조회: 회원 직접 연결 없이 기존 UNIQUE 키 사용
public interface ContentProgressRepository extends JpaRepository<ContentProgress, Long> {
    Optional<ContentProgress> findByEnrollmentIdAndCourseContentId(Long enrollmentId, Long courseContentId);

    @EntityGraph(attributePaths = "courseContent")
    List<ContentProgress> findByEnrollmentId(Long enrollmentId);
}

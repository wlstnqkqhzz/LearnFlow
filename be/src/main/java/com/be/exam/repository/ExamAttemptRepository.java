package com.be.exam.repository;

import com.be.exam.entity.ExamAttempt;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

// 응시 저장·이력 조회 및 관리자 구성 동결 검사
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {
    boolean existsByExamId(Long examId);
    List<ExamAttempt> findByEnrollmentIdOrderByAttemptNumberAsc(Long enrollmentId);
    boolean existsByEnrollmentIdAndSubmittedAtIsNotNullAndPassedTrue(Long enrollmentId);

    @Query("select a.enrollment.id from ExamAttempt a where a.id = :id")
    Optional<Long> findEnrollmentId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.id = :id")
    Optional<ExamAttempt> findForUpdate(@Param("id") Long id);
}

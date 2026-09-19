package com.be.assignment.repository;

import com.be.assignment.entity.AssignmentRule;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

// 경로의 courseId와 ruleId를 함께 검증하며, 배정 시에는 현재 읽기 사용
public interface AssignmentRuleRepository extends JpaRepository<AssignmentRule, Long> {
    List<AssignmentRule> findByCourseIdOrderByIdAsc(Long courseId);
    Optional<AssignmentRule> findByIdAndCourseId(Long id, Long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AssignmentRule r where r.id = :id and r.course.id = :courseId")
    Optional<AssignmentRule> findForUpdate(@Param("courseId") Long courseId, @Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AssignmentRule r where r.course.id = :courseId and r.isActive = true order by r.id")
    List<AssignmentRule> findActiveForAssignment(@Param("courseId") Long courseId);
}

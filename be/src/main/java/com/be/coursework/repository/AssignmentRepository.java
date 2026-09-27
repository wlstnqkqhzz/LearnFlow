package com.be.coursework.repository;

import com.be.coursework.entity.Assignment;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByCourseIdOrderBySortOrderAsc(Long courseId);
    Optional<Assignment> findByIdAndCourseId(Long id, Long courseId);
    boolean existsByCourseIdAndSortOrderAndIdNot(Long courseId, int sortOrder, Long id);
    boolean existsByCourseIdAndSortOrder(Long courseId, int sortOrder);
    boolean existsByCourseIdAndIsRequiredTrue(Long courseId);
    boolean existsByCourseIdAndIsRequiredTrueAndDueDateBefore(Long courseId, LocalDate today);

    @Query("""
        select count(a) > 0 from Assignment a where a.course.id = :courseId
        and (a.dueDate < :start or a.dueDate > :end)
        """)
    boolean hasOutsidePeriod(@Param("courseId") Long courseId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}

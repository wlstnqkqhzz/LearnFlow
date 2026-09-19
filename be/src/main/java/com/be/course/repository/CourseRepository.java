package com.be.course.repository;

import com.be.course.entity.Course;
import com.be.course.enums.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

// 교육과정 조회 및 변경 직렬화
public interface CourseRepository extends JpaRepository<Course, Long> {
    // 회원 변경 트리거에서 최신 OPEN 과정만 잠금 조회 (RR 스냅샷에 의한 누락 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.status = com.be.course.enums.CourseStatus.OPEN order by c.id")
    List<Course> findOpenForAssignment();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "instructor")
    @Query("""
            select c from Course c
            where (:status is null or c.status = :status)
              and (:type is null or c.courseType = :type)
              and (:instructorId is null or c.instructor.id = :instructorId)
              and (:keyword is null or lower(c.title) like lower(:keyword) escape '!')
            """)
    Page<Course> search(@Param("status") CourseStatus status, @Param("type") CourseType type,
                        @Param("instructorId") Long instructorId, @Param("keyword") String keyword,
                        Pageable pageable);
}

package com.be.course.repository;

import com.be.course.entity.Course;
import com.be.course.enums.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

// 교육과정 조회 및 변경 직렬화
public interface CourseRepository extends JpaRepository<Course, Long> {
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

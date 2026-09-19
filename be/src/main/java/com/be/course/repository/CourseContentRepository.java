package com.be.course.repository;

import com.be.course.entity.CourseContent;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

// 모든 개별 콘텐츠 조회는 교육과정 소속까지 함께 확인
public interface CourseContentRepository extends JpaRepository<CourseContent, Long> {
    Optional<CourseContent> findByIdAndCourseId(Long id, Long courseId);
    List<CourseContent> findByCourseIdOrderBySortOrderAsc(Long courseId);
    boolean existsByCourseIdAndSortOrder(Long courseId, int sortOrder);
    boolean existsByCourseIdAndSortOrderAndIdNot(Long courseId, int sortOrder, Long id);
}

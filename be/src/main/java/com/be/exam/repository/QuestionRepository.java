package com.be.exam.repository;

import com.be.exam.entity.Question;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

// 시험 소속을 포함하여 조회하고 목록은 항상 순서대로 반환
public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findByIdAndExamId(Long id, Long examId);
    List<Question> findByExamIdOrderBySortOrderAsc(Long examId);
    boolean existsByExamIdAndSortOrder(Long examId, int sortOrder);
    boolean existsByExamIdAndSortOrderAndIdNot(Long examId, int sortOrder, Long id);
}

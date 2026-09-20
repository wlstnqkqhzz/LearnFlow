package com.be.exam.repository;

import com.be.exam.entity.QuestionChoice;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

// 전체 문항의 선택지는 한 번에 조회하여 목록 N+1 방지
public interface QuestionChoiceRepository extends JpaRepository<QuestionChoice, Long> {
    Optional<QuestionChoice> findByIdAndQuestionId(Long id, Long questionId);
    List<QuestionChoice> findByQuestionIdOrderBySortOrderAsc(Long questionId);
    List<QuestionChoice> findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(Long examId);
}

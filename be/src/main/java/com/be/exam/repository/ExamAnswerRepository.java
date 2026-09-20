package com.be.exam.repository;

import com.be.exam.entity.ExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 답안 저장·일괄 채점 조회 및 기존 답안/선택지 삭제 보호
public interface ExamAnswerRepository extends JpaRepository<ExamAnswer, Long> {
    @EntityGraph(attributePaths = {"selectedChoices", "question"})
    List<ExamAnswer> findByExamAttemptId(Long attemptId);
    @EntityGraph(attributePaths = "selectedChoices")
    Optional<ExamAnswer> findByExamAttemptIdAndQuestionId(Long attemptId, Long questionId);
    boolean existsByQuestionId(Long questionId);

    @Query("select count(a) > 0 from ExamAnswer a join a.selectedChoices c where c.id = :choiceId")
    boolean referencesChoice(@Param("choiceId") Long choiceId);
}

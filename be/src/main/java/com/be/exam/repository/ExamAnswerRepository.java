package com.be.exam.repository;

import com.be.exam.entity.ExamAnswer;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 기존 답안/선택지 FK를 삭제하기 전에 참조 여부만 확인
public interface ExamAnswerRepository extends Repository<ExamAnswer, Long> {
    boolean existsByQuestionId(Long questionId);

    @Query("select count(a) > 0 from ExamAnswer a join a.selectedChoices c where c.id = :choiceId")
    boolean referencesChoice(@Param("choiceId") Long choiceId);
}

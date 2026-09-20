package com.be.exam.repository;

import com.be.exam.entity.ExamAttempt;
import org.springframework.data.repository.Repository;

// 삭제 보호를 위한 이력 존재 확인만 제공; 응시 생성/제출 기능 없음
public interface ExamAttemptRepository extends Repository<ExamAttempt, Long> {
    boolean existsByExamId(Long examId);
}

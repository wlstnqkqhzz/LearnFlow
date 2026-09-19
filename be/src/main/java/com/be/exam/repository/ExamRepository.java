package com.be.exam.repository;

import com.be.exam.entity.Exam;
import org.springframework.data.repository.Repository;

// 이번 단계에서는 수료 판정에 필요한 시험 존재 여부만 제공
public interface ExamRepository extends Repository<Exam, Long> {
    boolean existsByCourseId(Long courseId);
}

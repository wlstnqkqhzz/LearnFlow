package com.be.exam.repository;

import com.be.exam.entity.Exam;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 과정별 단일 시험 관리 및 기존 수료 판정의 존재 조회를 함께 지원
public interface ExamRepository extends JpaRepository<Exam, Long> {
    boolean existsByCourseId(Long courseId);
    Optional<Exam> findByCourseId(Long courseId);
}

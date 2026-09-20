package com.be.exam.dto;
import com.be.exam.entity.ExamAttempt;
import java.math.BigDecimal;
import java.time.LocalDateTime;
// 제출/결과/이력 공통 요약: 정답이나 문항별 채점 정보 없음
public record AttemptResponse(Long attemptId, int attemptNumber, BigDecimal score, Boolean passed,
        LocalDateTime startedAt, LocalDateTime submittedAt, int maxAttempts, int remainingAttempts) {
    public static AttemptResponse from(ExamAttempt a, int allocated) {
        return new AttemptResponse(a.getId(), a.getAttemptNumber(), a.getScore(), a.getPassed(),
                a.getStartedAt(), a.getSubmittedAt(), a.getExam().getMaxAttempts(),
                Math.max(0, a.getExam().getMaxAttempts() - allocated));
    }
}

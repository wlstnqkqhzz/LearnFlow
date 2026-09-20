package com.be.exam.dto;
import com.be.exam.entity.*;
import java.math.BigDecimal;
import java.util.List;
// 정규화 합격 기준과 문항 원점수 합계를 명확히 구분
public record ExamResponse(Long examId, Long courseId, String title, BigDecimal passingScore,
        int maxAttempts, int questionCount, BigDecimal totalQuestionScore) {
    public static ExamResponse from(Exam exam, List<Question> questions) {
        return new ExamResponse(exam.getId(), exam.getCourse().getId(), exam.getTitle(), exam.getPassingScore(),
                exam.getMaxAttempts(), questions.size(),
                questions.stream().map(Question::getScore).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}

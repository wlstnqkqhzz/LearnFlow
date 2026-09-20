package com.be.exam;

import com.be.assignment.AssignmentFixtures;
import com.be.enrollment.entity.Enrollment;
import com.be.exam.entity.*;
import com.be.exam.enums.QuestionType;
import com.be.exam.service.ExamGradingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import static com.be.exam.ExamFixtures.*;
import static org.assertj.core.api.Assertions.*;

// 채점 단위 테스트: 원점수와 정규화 점수를 별도로 검증
class ExamGradingServiceTest {
    final ExamGradingService grading = new ExamGradingService();
    final Exam exam = exam();
    final ExamAttempt attempt = ExamAttempt.start(Enrollment.manual(AssignmentFixtures.member(2L), exam.getCourse(),
            LocalDateTime.now(AssignmentFixtures.CLOCK)), exam, 1, LocalDateTime.now(AssignmentFixtures.CLOCK));

    @ParameterizedTest @EnumSource(value = QuestionType.class, names = {"SINGLE_CHOICE", "TRUE_FALSE"})
    void exactChoiceEarnsFullScoreOtherwiseZero(QuestionType type) {
        var q = question(exam, 1, type); var choices = choices(q);
        var answer = ExamAnswer.create(attempt, q);
        answer.replaceChoices(Set.of(choices.getFirst()));
        assertThat(grading.grade(List.of(q), choices, List.of(answer))).isEqualByComparingTo("100");
        assertThat(answer.getEarnedScore()).isEqualByComparingTo("10");
        answer.replaceChoices(Set.of(choices.getLast()));
        assertThat(grading.grade(List.of(q), choices, List.of(answer))).isEqualByComparingTo("0");
    }

    @ParameterizedTest @ValueSource(strings = {"exact", "reverse", "subset", "extra", "empty"})
    void multipleComparesExactSetsWithoutPartialCredit(String mode) {
        var q = question(exam, 1, QuestionType.MULTIPLE_CHOICE);
        var a = choice(q, 1, "A", true, 1); var b = choice(q, 2, "B", false, 2); var c = choice(q, 3, "C", true, 3);
        var answer = ExamAnswer.create(attempt, q);
        answer.replaceChoices(switch (mode) { case "exact" -> new LinkedHashSet<>(List.of(a, c));
            case "reverse" -> new LinkedHashSet<>(List.of(c, a)); case "subset" -> Set.of(a); case "extra" -> Set.of(a, b, c); default -> Set.of(); });
        assertThat(grading.grade(List.of(q), List.of(a, b, c), List.of(answer)))
                .isEqualByComparingTo(mode.equals("exact") || mode.equals("reverse") ? "100" : "0");
    }

    @Test void unansweredStillCountsInDenominator() {
        var q1 = question(exam, 1, QuestionType.SINGLE_CHOICE);
        var q2 = question(exam, 2, QuestionType.SINGLE_CHOICE); q2.update("Q2", q2.getQuestionType(), new BigDecimal("20"), 2);
        var q3 = question(exam, 3, QuestionType.SINGLE_CHOICE); q3.update("Q3", q3.getQuestionType(), new BigDecimal("30"), 3);
        var c1 = choice(q1, 1, "A", true, 1); var c2 = choice(q2, 2, "A", true, 1); var c3 = choice(q3, 3, "A", true, 1);
        var a1 = ExamAnswer.create(attempt, q1); a1.replaceChoices(Set.of(c1));
        var a2 = ExamAnswer.create(attempt, q2); a2.replaceChoices(Set.of(c2));
        assertThat(grading.grade(List.of(q1, q2, q3), List.of(c1, c2, c3), List.of(a1, a2))).isEqualByComparingTo("50.00");
        assertThat(a1.getEarnedScore().add(a2.getEarnedScore())).isEqualByComparingTo("30");
    }

    @Test void roundsHalfUpAtSecondDecimal() {
        var q1 = question(exam, 1, QuestionType.SINGLE_CHOICE); q1.update("Q1", q1.getQuestionType(), new BigDecimal("8333.50"), 1);
        var q2 = question(exam, 2, QuestionType.SINGLE_CHOICE); q2.update("Q2", q2.getQuestionType(), new BigDecimal("1666.50"), 2);
        var c1 = choice(q1, 1, "A", true, 1); var c2 = choice(q2, 2, "A", true, 1);
        var answer = ExamAnswer.create(attempt, q1); answer.replaceChoices(Set.of(c1));
        assertThat(grading.grade(List.of(q1, q2), List.of(c1, c2), List.of(answer))).isEqualByComparingTo("83.34");
    }
}

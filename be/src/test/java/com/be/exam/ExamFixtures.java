package com.be.exam;

import com.be.assignment.AssignmentFixtures;
import com.be.course.enums.CourseStatus;
import com.be.exam.entity.*;
import com.be.exam.enums.QuestionType;
import java.math.BigDecimal;
import java.util.*;

// 실제 Entity를 사용하는 시험 관리 테스트 공통 데이터
final class ExamFixtures {
    static Exam exam() {
        var exam = Exam.create(AssignmentFixtures.course(CourseStatus.OPEN), "시험", new BigDecimal("80"), 3);
        AssignmentFixtures.id(exam, 10L);
        return exam;
    }
    static Question question(Exam exam, long id, QuestionType type) {
        var question = Question.create(exam, "문제", type, BigDecimal.TEN, (int) id);
        AssignmentFixtures.id(question, id);
        return question;
    }
    static QuestionChoice choice(Question question, long id, String text, boolean correct, int order) {
        var choice = QuestionChoice.create(question, text, correct, order);
        AssignmentFixtures.id(choice, id);
        return choice;
    }
    static List<QuestionChoice> choices(Question question) {
        return List.of(choice(question, 100L, "TRUE", true, 1), choice(question, 101L, "FALSE", false, 2));
    }
}

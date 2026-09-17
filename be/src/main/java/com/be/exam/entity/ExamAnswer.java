package com.be.exam.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 시험 답안 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "exam_answers",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_exam_answers_attempt_question", columnNames = {"exam_attempt_id", "question_id"})
        },
        indexes = {
                @Index(name = "idx_exam_answers_question", columnList = "question_id")
        },
        check = {
                @CheckConstraint(name = "chk_exam_answers_grading",
                        constraint = "(is_correct IS NULL AND earned_score IS NULL) OR (is_correct IS NOT NULL AND is_correct IN (0, 1) AND earned_score IS NOT NULL AND earned_score >= 0)")
        })
public class ExamAnswer {

    // 시험 답안 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 답안이 속한 시험 응시 이력
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_attempt_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exam_answers_attempt", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private ExamAttempt examAttempt;

    // 해당 선택지 또는 답안의 문제
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exam_answers_question", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Question question;

    // 답안 정답 여부 (채점 전에는 null)
    @Column(name = "is_correct", nullable = true, columnDefinition = "boolean")
    private Boolean isCorrect;

    // 해당 문제의 획득 배점 (채점 전에는 null)
    @Column(name = "earned_score", nullable = true, precision = 7, scale = 2)
    private BigDecimal earnedScore;

    // 선택한 선택지 목록 - exam_answer_choices 연결 테이블에 저장
    @Getter(AccessLevel.NONE)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "exam_answer_choices",
            joinColumns = @JoinColumn(name = "exam_answer_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "question_choice_id", nullable = false),
            foreignKey = @ForeignKey(name = "fk_exam_answer_choices_answer",
                    options = "ON DELETE RESTRICT ON UPDATE RESTRICT"),
            inverseForeignKey = @ForeignKey(name = "fk_exam_answer_choices_question_choice",
                    options = "ON DELETE RESTRICT ON UPDATE RESTRICT"),
            indexes = @Index(name = "idx_exam_answer_choices_question_choice",
                    columnList = "question_choice_id"),
            options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")
    private Set<QuestionChoice> selectedChoices = new HashSet<>();

    // 외부에서 선택 답안을 직접 수정하지 못하도록 읽기 전용 뷰 반환
    public Set<QuestionChoice> getSelectedChoices() {
        return Collections.unmodifiableSet(selectedChoices);
    }
}

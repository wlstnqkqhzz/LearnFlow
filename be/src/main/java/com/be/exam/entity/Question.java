package com.be.exam.entity;

import com.be.exam.enums.QuestionType;
import com.be.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 시험 문제 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "questions",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_questions_exam_sort_order", columnNames = {"exam_id", "sort_order"})
        },
        check = {
                @CheckConstraint(name = "chk_questions_score",
                        constraint = "score > 0"),
                @CheckConstraint(name = "chk_questions_sort_order",
                        constraint = "sort_order > 0"),
                @CheckConstraint(name = "chk_questions_question_type",
                        constraint = "CAST(question_type AS BINARY) IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE')")
        })
public class Question extends BaseTimeEntity {

    // 시험 문제 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 소속 시험
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_questions_exam", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Exam exam;

    // 문제 내용
    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    // 문제 유형
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuestionType questionType;

    // 문제 배점 (0보다 큰 값)
    @Column(name = "score", nullable = false, precision = 7, scale = 2)
    private BigDecimal score;

    // 시험 내 문제 순서 (1부터 시작)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 문항 및 선택지 구성은 Service에서 함께 검증한 뒤 저장
    public static Question create(Exam exam, String text, QuestionType type, BigDecimal score, int order) {
        Question question = new Question();
        question.exam = exam;
        question.update(text, type, score, order);
        return question;
    }

    public void update(String text, QuestionType type, BigDecimal score, int order) {
        this.questionText = text;
        this.questionType = type;
        this.score = score;
        this.sortOrder = order;
    }

    // UNIQUE 충돌을 피하는 전체 순서 변경에 사용
    public void changeSortOrder(int order) { this.sortOrder = order; }
}

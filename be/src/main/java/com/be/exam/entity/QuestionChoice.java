package com.be.exam.entity;

import com.be.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 문제 선택지 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "question_choices",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_question_choices_question_sort_order", columnNames = {"question_id", "sort_order"})
        },
        check = {
                @CheckConstraint(name = "chk_question_choices_is_correct",
                        constraint = "is_correct IN (0, 1)"),
                @CheckConstraint(name = "chk_question_choices_sort_order",
                        constraint = "sort_order > 0")
        })
public class QuestionChoice extends BaseTimeEntity {

    // 문제 선택지 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 해당 선택지 또는 답안의 문제
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_question_choices_question", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Question question;

    // 선택지 내용
    @Column(name = "choice_text", nullable = false, length = 1000)
    private String choiceText;

    // 정답 선택지 여부
    @Column(name = "is_correct", nullable = false, columnDefinition = "boolean")
    private boolean isCorrect;

    // 문제 내 선택지 순서 (1부터 시작)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}

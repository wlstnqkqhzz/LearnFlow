package com.be.exam.entity;

import com.be.course.entity.Course;
import com.be.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 시험 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "exams",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_exams_course", columnNames = {"course_id"})
        },
        check = {
                @CheckConstraint(name = "chk_exams_passing_score",
                        constraint = "passing_score BETWEEN 0 AND 100"),
                @CheckConstraint(name = "chk_exams_max_attempts",
                        constraint = "max_attempts > 0")
        })
public class Exam extends BaseTimeEntity {

    // 시험 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 시험이 속한 교육과정 (과정당 최대 1개 시험)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exams_course", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Course course;

    // 시험명
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 시험 합격 기준 점수 (0~100)
    @Column(name = "passing_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal passingScore;

    // 최대 시험 응시 횟수
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    // 소속 과정은 생성 시 고정하고 설정만 변경 가능
    public static Exam create(Course course, String title, BigDecimal passingScore, int maxAttempts) {
        Exam exam = new Exam();
        exam.course = course;
        exam.update(title, passingScore, maxAttempts);
        return exam;
    }

    // 입력 범위 검증은 Service 경계에서 수행
    public void update(String title, BigDecimal passingScore, int maxAttempts) {
        this.title = title;
        this.passingScore = passingScore;
        this.maxAttempts = maxAttempts;
    }
}

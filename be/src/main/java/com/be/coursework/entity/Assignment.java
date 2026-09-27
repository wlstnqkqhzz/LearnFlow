package com.be.coursework.entity;

import com.be.course.entity.Course;
import com.be.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "assignments", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    uniqueConstraints = @UniqueConstraint(name = "uk_assignments_course_sort_order", columnNames = {"course_id", "sort_order"}),
    indexes = @Index(name = "idx_assignments_course_required_due", columnList = "course_id, is_required, due_date"),
    check = {
        @CheckConstraint(name = "chk_assignments_score", constraint = "passing_score BETWEEN 0 AND 100"),
        @CheckConstraint(name = "chk_assignments_order", constraint = "sort_order > 0"),
        @CheckConstraint(name = "chk_assignments_required", constraint = "is_required IN (0, 1)")
    })
public class Assignment extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_assignments_course", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Course course;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "is_required", nullable = false, columnDefinition = "boolean")
    private boolean isRequired;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(name = "passing_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal passingScore;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static Assignment create(Course course, String title, String description, boolean required,
            LocalDate dueDate, BigDecimal passingScore, int sortOrder) {
        var assignment = new Assignment();
        assignment.course = java.util.Objects.requireNonNull(course);
        assignment.update(title, description, required, dueDate, passingScore, sortOrder);
        return assignment;
    }
    public void update(String title, String description, boolean required, LocalDate dueDate, BigDecimal passingScore, int sortOrder) {
        this.title = title.trim();
        this.description = description;
        this.isRequired = required;
        this.dueDate = dueDate;
        this.passingScore = passingScore;
        this.sortOrder = sortOrder;
    }
    public void changeSortOrder(int order) { this.sortOrder = order; }
}

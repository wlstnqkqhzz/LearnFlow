package com.be.retraining.entity;

import com.be.course.entity.Course;
import com.be.global.entity.BaseTimeEntity;
import com.be.global.exception.*;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "retraining_policies", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    indexes = @Index(name = "idx_retraining_due", columnList = "enabled, auto_create, next_generation_date, id"),
    check = @CheckConstraint(name = "chk_retraining_schedule", constraint = "interval_months BETWEEN 1 AND 1200 AND duration_days BETWEEN 1 AND 3660 AND generation_lead_days BETWEEN 0 AND 3660 AND next_occurrence_number > 0"))
public class RetrainingPolicy extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_course_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_retraining_source", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Course sourceCourse;
    @Column(name = "base_title", nullable = false, length = 150) private String baseTitle;
    @Column(nullable = false, columnDefinition = "boolean") private boolean enabled;
    @Column(name = "auto_create", nullable = false, columnDefinition = "boolean") private boolean autoCreate;
    @Column(name = "auto_open", nullable = false, columnDefinition = "boolean") private boolean autoOpen;
    @Column(name = "interval_months", nullable = false) private int intervalMonths;
    @Column(name = "first_start_date", nullable = false) private LocalDate firstStartDate;
    @Column(name = "duration_days", nullable = false) private int durationDays;
    @Column(name = "generation_lead_days", nullable = false) private int generationLeadDays;
    @Column(name = "next_occurrence_number", nullable = false) private int nextOccurrenceNumber = 1;
    @Column(name = "next_generation_date", nullable = false) private LocalDate nextGenerationDate;

    public static RetrainingPolicy create(Course source, String title, boolean enabled, boolean autoCreate,
            boolean autoOpen, int months, LocalDate first, int days, int lead) {
        var policy = new RetrainingPolicy();
        policy.configure(source, title, enabled, autoCreate, autoOpen, months, first, days, lead);
        return policy;
    }

    public void configure(Course source, String title, boolean enabled, boolean autoCreate,
            boolean autoOpen, int months, LocalDate first, int days, int lead) {
        if (source == null || title == null || title.isBlank() || title.trim().length() > 150
                || first == null || months < 1 || months > 1200 || days < 1 || days > 3660 || lead < 0 || lead > 3660)
            throw new BusinessException(ErrorCode.INVALID_RETRAINING_SCHEDULE);
        // skip으로 소비한 회차도 다시 다른 일정으로 사용하지 않는다.
        if (nextOccurrenceNumber > 1 && (!sourceCourse.getId().equals(source.getId())
                || intervalMonths != months || !firstStartDate.equals(first) || durationDays != days || generationLeadDays != lead))
            throw new BusinessException(ErrorCode.RETRAINING_SCHEDULE_LOCKED);
        this.sourceCourse = source;
        this.baseTitle = title.trim();
        this.enabled = enabled;
        this.autoCreate = autoCreate;
        this.autoOpen = autoOpen;
        this.intervalMonths = months;
        this.firstStartDate = first;
        this.durationDays = days;
        this.generationLeadDays = lead;
        this.nextGenerationDate = dates(nextOccurrenceNumber).generationDate();
    }

    public record Dates(LocalDate startDate, LocalDate endDate, LocalDate generationDate) {}

    public Dates dates(int number) {
        if (number < 1) throw new BusinessException(ErrorCode.INVALID_RETRAINING_OCCURRENCE);
        try {
            LocalDate start = firstStartDate.plusMonths(Math.multiplyExact((long) number - 1, intervalMonths));
            LocalDate end = start.plusDays(durationDays - 1L);
            LocalDate generation = start.minusDays(generationLeadDays);
            if (generation.getYear() < 1000 || end.getYear() > 9999) throw new DateTimeException("Unsupported DB date");
            return new Dates(start, end, generation);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_RETRAINING_SCHEDULE);
        }
    }

    public String title(int number) {
        var start = dates(number).startDate();
        return baseTitle + " " + java.time.format.DateTimeFormatter.ofPattern("uuuu-MM").format(start) + " · " + number + "회차";
    }

    public void advance() {
        if (nextOccurrenceNumber == Integer.MAX_VALUE) throw new BusinessException(ErrorCode.INVALID_RETRAINING_SCHEDULE);
        LocalDate next = dates(nextOccurrenceNumber + 1).generationDate();
        nextOccurrenceNumber++;
        nextGenerationDate = next;
    }

    public void changeEnabled(boolean enabled) { this.enabled = enabled; }
}

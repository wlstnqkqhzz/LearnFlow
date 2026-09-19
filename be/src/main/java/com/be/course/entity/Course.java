package com.be.course.entity;

import com.be.course.enums.CourseStatus;
import com.be.course.enums.CourseType;
import com.be.global.entity.BaseTimeEntity;
import com.be.member.entity.Member;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 교육과정 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "courses",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        indexes = {
                @Index(name = "idx_courses_instructor_status", columnList = "instructor_id, status"),
                @Index(name = "idx_courses_status_period", columnList = "status, start_date, end_date")
        },
        check = {
                @CheckConstraint(name = "chk_courses_passing_progress_rate",
                        constraint = "passing_progress_rate BETWEEN 0 AND 100"),
                @CheckConstraint(name = "chk_courses_period",
                        constraint = "start_date IS NULL OR end_date IS NULL OR start_date <= end_date"),
                @CheckConstraint(name = "chk_courses_published_fields",
                        constraint = "status = 'DRAFT' OR (start_date IS NOT NULL AND end_date IS NOT NULL)"),
                @CheckConstraint(name = "chk_courses_course_type",
                        constraint = "CAST(course_type AS BINARY) IN ('MANDATORY', 'OPTIONAL')"),
                @CheckConstraint(name = "chk_courses_status",
                        constraint = "CAST(status AS BINARY) IN ('DRAFT', 'OPEN', 'CLOSED')")
        })
public class Course extends BaseTimeEntity {

    // 교육과정 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 교육과정명
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 교육과정 설명
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    // 필수 / 선택 교육 구분
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "course_type", nullable = false, length = 20)
    private CourseType courseType;

    // 교육과정 상태 (작성 중 / 공개 / 종료)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'DRAFT'")
    @Column(name = "status", nullable = false, length = 20)
    private CourseStatus status = CourseStatus.DRAFT;

    // 교육 시작일 (초안에서는 null 허용)
    @Column(name = "start_date", nullable = true)
    private LocalDate startDate;

    // 교육 종료일 (초안에서는 null 허용)
    @Column(name = "end_date", nullable = true)
    private LocalDate endDate;

    // 수료에 필요한 필수 콘텐츠 평균 진도율 (0~100)
    @ColumnDefault("100.00")
    @Column(name = "passing_progress_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal passingProgressRate = new BigDecimal("100.00");

    // 담당 강사 (모든 상태에서 null 허용, 지정 시 INSTRUCTOR 역할을 Service에서 검증)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "instructor_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_courses_instructor", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Member instructor;

    // Service 검증을 통과한 정보로 초안 생성 (외부에서 생성 상태 지정 불가)
    public static Course create(String title, String description, CourseType courseType,
                                LocalDate startDate, LocalDate endDate, BigDecimal passingProgressRate,
                                Member instructor) {
        Course course = new Course();
        course.update(title, description, courseType, startDate, endDate, passingProgressRate, instructor);
        return course;
    }

    // 상태·식별자와 분리된 일반 정보 수정
    public void update(String title, String description, CourseType courseType,
                       LocalDate startDate, LocalDate endDate, BigDecimal passingProgressRate, Member instructor) {
        this.title = title;
        this.description = description;
        this.courseType = courseType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.passingProgressRate = passingProgressRate;
        this.instructor = instructor;
    }

    // 전이 및 공개 조건 검증은 CourseService에서 수행
    public void changeStatus(CourseStatus status) {
        this.status = status;
    }
}

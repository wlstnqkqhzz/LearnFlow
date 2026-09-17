package com.be.course.entity;

import com.be.course.enums.ContentType;
import com.be.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 교육 콘텐츠 Entity

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "course_contents",
        options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_course_contents_course_sort_order", columnNames = {"course_id", "sort_order"})
        },
        check = {
                @CheckConstraint(name = "chk_course_contents_duration_seconds",
                        constraint = "duration_seconds IS NULL OR duration_seconds >= 0"),
                @CheckConstraint(name = "chk_course_contents_sort_order",
                        constraint = "sort_order > 0"),
                @CheckConstraint(name = "chk_course_contents_is_required",
                        constraint = "is_required IN (0, 1)"),
                @CheckConstraint(name = "chk_course_contents_content_type",
                        constraint = "CAST(content_type AS BINARY) IN ('VIDEO', 'DOCUMENT', 'LINK')")
        })
public class CourseContent extends BaseTimeEntity {

    // 교육 콘텐츠 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 소속 교육과정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_course_contents_course", options = "ON DELETE RESTRICT ON UPDATE RESTRICT"))
    private Course course;

    // 콘텐츠명
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 콘텐츠 유형 (동영상 / 문서 / 링크)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    // 콘텐츠 접근 URL
    @Column(name = "content_url", nullable = false, length = 2048)
    private String contentUrl;

    // 콘텐츠 길이 (초 단위, 미지정 시 null)
    @Column(name = "duration_seconds", nullable = true)
    private Integer durationSeconds;

    // 교육과정 내 콘텐츠 순서 (1부터 시작)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 수료 진도율 계산에 포함할 필수 콘텐츠 여부
    @ColumnDefault("true")
    @Column(name = "is_required", nullable = false, columnDefinition = "boolean")
    private boolean isRequired = true;
}

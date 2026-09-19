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

    // 소속 교육과정은 생성 시 고정
    public static CourseContent create(Course course, String title, ContentType contentType,
                                       String contentUrl, Integer durationSeconds, int sortOrder, boolean required) {
        CourseContent content = new CourseContent();
        content.course = course;
        content.update(title, contentType, contentUrl, durationSeconds, sortOrder, required);
        return content;
    }

    // Service에서 소속 및 순서 충돌을 검증한 뒤 콘텐츠 정보 수정
    public void update(String title, ContentType contentType, String contentUrl,
                       Integer durationSeconds, int sortOrder, boolean required) {
        this.title = title;
        this.contentType = contentType;
        this.contentUrl = contentUrl;
        this.durationSeconds = durationSeconds;
        this.sortOrder = sortOrder;
        this.isRequired = required;
    }

    // 전체 재정렬 시 사용하며 항상 양수 순서 유지
    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}

package com.be.course.dto;

import com.be.course.enums.CourseType;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import lombok.Getter;

// 생략 필드는 유지하고 nullable 필드의 명시적 null은 값 제거
@Getter
public class CoursePatchRequest {
    // 변경할 제목
    @Size(max = 200) @Pattern(regexp = "(?s).*\\S.*")
    private String title;
    // 설명 (null로 제거 가능)
    @Size(max = 16383)
    private String description;
    @JsonIgnore
    private boolean descriptionPresent;
    // 필수/선택 분류
    private CourseType courseType;
    // 시작일 (초안에서만 제거 가능)
    private LocalDate startDate;
    @JsonIgnore
    private boolean startDatePresent;
    // 종료일 (초안에서만 제거 가능)
    private LocalDate endDate;
    @JsonIgnore
    private boolean endDatePresent;
    // 수료 기준 진도율
    @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
    private BigDecimal passingProgressRate;
    // 강사 ID (null로 배정 해제 가능)
    @Positive
    private Long instructorId;
    @JsonIgnore
    private boolean instructorIdPresent;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTitle(String title) {
        this.title = title.trim();
    }

    @JsonSetter
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setCourseType(CourseType courseType) {
        this.courseType = courseType;
    }

    @JsonSetter
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.startDatePresent = true;
    }

    @JsonSetter
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDatePresent = true;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setPassingProgressRate(BigDecimal passingProgressRate) {
        this.passingProgressRate = passingProgressRate;
    }

    @JsonSetter
    public void setInstructorId(Long instructorId) {
        this.instructorId = instructorId;
        this.instructorIdPresent = true;
    }
}

package com.be.organization.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;
import lombok.Getter;

// 이름·상위 부서 부분 수정 - parentDepartmentId의 명시적 null은 상위 부서 해제
@Getter
public class DepartmentPatchRequest {
    @Size(max = 100) @Pattern(regexp = ".*\\S.*")
    private String name;

    @Positive
    private Long parentDepartmentId;

    // 생략과 명시적 null을 구분하는 내부 플래그
    @JsonIgnore
    private boolean parentDepartmentSpecified;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setName(String name) {
        this.name = name.trim();
    }

    @JsonSetter("parentDepartmentId")
    public void setParentDepartmentId(Long parentDepartmentId) {
        this.parentDepartmentId = parentDepartmentId;
        this.parentDepartmentSpecified = true;
    }
}

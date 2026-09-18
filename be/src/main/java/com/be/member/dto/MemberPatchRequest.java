package com.be.member.dto;

import com.be.member.entity.Member;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.Getter;

// 생략한 필드는 유지하며 명시적 null은 허용하지 않는 일반 정보 부분 수정
@Getter
public class MemberPatchRequest {
    // 변경할 이메일 (공백 제거 및 소문자 정규화 후 검증)
    @Email @Size(max = 255) @Pattern(regexp = ".*\\S.*")
    private String email;

    // 변경할 이름
    @Size(max = 100) @Pattern(regexp = ".*\\S.*")
    private String name;

    // 변경할 입사일
    private LocalDate hireDate;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setEmail(String email) {
        this.email = Member.normalizeEmail(email);
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setName(String name) {
        this.name = name.trim();
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }
}

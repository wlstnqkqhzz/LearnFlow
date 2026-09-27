package com.be.coursework.dto;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
@Getter
public class AssignmentPatchRequest {
    @Size(max = 200) @Pattern(regexp = "(?s).*\\S.*") private String title;
    @Size(max = 10000) private String description;
    @JsonIgnore private boolean descriptionPresent;
    private Boolean required;
    private LocalDate dueDate;
    @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) private BigDecimal passingScore;
    @Min(1) private Integer sortOrder;
    @JsonSetter(nulls = Nulls.FAIL) public void setTitle(String value) { title = value.trim(); }
    @JsonSetter public void setDescription(String value) { description = value; descriptionPresent = true; }
    @JsonSetter(nulls = Nulls.FAIL) public void setRequired(Boolean value) { required = value; }
    @JsonSetter(nulls = Nulls.FAIL) public void setDueDate(LocalDate value) { dueDate = value; }
    @JsonSetter(nulls = Nulls.FAIL) public void setPassingScore(BigDecimal value) { passingScore = value; }
    @JsonSetter(nulls = Nulls.FAIL) public void setSortOrder(Integer value) { sortOrder = value; }
}

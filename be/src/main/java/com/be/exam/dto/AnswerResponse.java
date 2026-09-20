package com.be.exam.dto;
import java.util.List;
// 선택한 값만 반환하고 제출 전 채점 정보는 제공하지 않음
public record AnswerResponse(Long questionId, List<Long> selectedChoiceIds) {}

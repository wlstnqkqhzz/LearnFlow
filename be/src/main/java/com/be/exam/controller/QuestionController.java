package com.be.exam.controller;

import com.be.exam.dto.*;
import com.be.exam.service.QuestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 관리자 전용 문항·선택지 관리; 응답의 정답 정보를 직원에게 노출하지 않음
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses/{courseId}/exam/questions")
public class QuestionController {
    private final QuestionService service;

    @PostMapping
    public ResponseEntity<QuestionAdminResponse> create(@PathVariable @Positive Long courseId,
            @RequestBody @Valid QuestionCreateRequest request) {
        var response = service.create(courseId, request);
        return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/exam/questions/" + response.questionId())).body(response);
    }

    @GetMapping
    public List<QuestionAdminResponse> getAll(@PathVariable @Positive Long courseId) { return service.getAll(courseId); }

    @GetMapping("/{questionId}")
    public QuestionAdminResponse get(@PathVariable @Positive Long courseId, @PathVariable @Positive Long questionId) {
        return service.get(courseId, questionId);
    }

    @PatchMapping("/{questionId}")
    public QuestionAdminResponse update(@PathVariable @Positive Long courseId, @PathVariable @Positive Long questionId,
            @RequestBody @Valid QuestionPatchRequest request) { return service.update(courseId, questionId, request); }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long courseId, @PathVariable @Positive Long questionId) {
        service.delete(courseId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/order")
    public List<QuestionAdminResponse> reorder(@PathVariable @Positive Long courseId,
            @RequestBody @Valid QuestionOrderRequest request) { return service.reorder(courseId, request); }

    @PostMapping("/{questionId}/choices")
    public ResponseEntity<ChoiceAdminResponse> createChoice(@PathVariable @Positive Long courseId,
            @PathVariable @Positive Long questionId, @RequestBody @Valid ChoiceCreateRequest request) {
        var response = service.createChoice(courseId, questionId, request);
        return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/exam/questions/" + questionId
                + "/choices/" + response.choiceId())).body(response);
    }

    @GetMapping("/{questionId}/choices")
    public List<ChoiceAdminResponse> getChoices(@PathVariable @Positive Long courseId, @PathVariable @Positive Long questionId) {
        return service.getChoices(courseId, questionId);
    }

    @GetMapping("/{questionId}/choices/{choiceId}")
    public ChoiceAdminResponse getChoice(@PathVariable @Positive Long courseId, @PathVariable @Positive Long questionId,
            @PathVariable @Positive Long choiceId) { return service.getChoice(courseId, questionId, choiceId); }

    @PatchMapping("/{questionId}/choices/{choiceId}")
    public ChoiceAdminResponse updateChoice(@PathVariable @Positive Long courseId, @PathVariable @Positive Long questionId,
            @PathVariable @Positive Long choiceId, @RequestBody @Valid ChoicePatchRequest request) {
        return service.updateChoice(courseId, questionId, choiceId, request);
    }

    @DeleteMapping("/{questionId}/choices/{choiceId}")
    public ResponseEntity<Void> deleteChoice(@PathVariable @Positive Long courseId, @PathVariable @Positive Long questionId,
            @PathVariable @Positive Long choiceId) {
        service.deleteChoice(courseId, questionId, choiceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{questionId}/choices/order")
    public List<ChoiceAdminResponse> reorderChoices(@PathVariable @Positive Long courseId, @PathVariable @Positive Long questionId,
            @RequestBody @Valid ChoiceOrderRequest request) { return service.reorderChoices(courseId, questionId, request); }
}

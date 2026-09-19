package com.be.assignment.controller;

import com.be.assignment.dto.*;
import com.be.assignment.service.AssignmentRuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// ADMIN 전용 규칙 관리 API (물리 삭제 없음)
@RestController
@RequestMapping("/api/courses/{courseId}/assignment-rules")
@RequiredArgsConstructor
public class AssignmentRuleController {
    private final AssignmentRuleService service;

    @PostMapping
    public ResponseEntity<AssignmentRuleResponse> create(@PathVariable @Positive Long courseId,
            @Valid @RequestBody AssignmentRuleCreateRequest request) {
        var response = service.create(courseId, request);
        return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/assignment-rules/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<AssignmentRuleResponse> getAll(@PathVariable @Positive Long courseId) {
        return service.getAll(courseId);
    }

    @GetMapping("/{ruleId}")
    public AssignmentRuleResponse get(@PathVariable @Positive Long courseId, @PathVariable @Positive Long ruleId) {
        return service.get(courseId, ruleId);
    }

    @PatchMapping("/{ruleId}")
    public AssignmentRuleResponse update(@PathVariable @Positive Long courseId, @PathVariable @Positive Long ruleId,
            @Valid @RequestBody AssignmentRuleUpdateRequest request) {
        return service.update(courseId, ruleId, request);
    }

    @PatchMapping("/{ruleId}/status")
    public AssignmentRuleResponse changeStatus(@PathVariable @Positive Long courseId, @PathVariable @Positive Long ruleId,
            @Valid @RequestBody AssignmentRuleStatusRequest request) {
        return service.changeStatus(courseId, ruleId, request);
    }
}

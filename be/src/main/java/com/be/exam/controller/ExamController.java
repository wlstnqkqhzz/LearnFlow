package com.be.exam.controller;

import com.be.exam.dto.*;
import com.be.exam.service.ExamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// ADMIN 전용 시험 설정 API; 시험 삭제와 응시 API는 제공하지 않음
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses/{courseId}/exam")
public class ExamController {
    private final ExamService service;

    @PostMapping
    public ResponseEntity<ExamResponse> create(@PathVariable @Positive Long courseId,
            @RequestBody @Valid ExamCreateRequest request) {
        var response = service.create(courseId, request);
        return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/exam")).body(response);
    }

    @GetMapping
    public ExamResponse get(@PathVariable @Positive Long courseId) { return service.get(courseId); }

    @PatchMapping
    public ExamResponse update(@PathVariable @Positive Long courseId, @RequestBody @Valid ExamPatchRequest request) {
        return service.update(courseId, request);
    }

    @GetMapping("/validation")
    public ExamResponse validate(@PathVariable @Positive Long courseId) { return service.validateConfiguration(courseId); }
}

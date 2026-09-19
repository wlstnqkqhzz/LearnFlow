package com.be.course.controller;

import com.be.course.dto.*;
import com.be.course.service.CourseContentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 소속 교육과정을 경로로 명시하는 콘텐츠 관리 API
@RestController
@RequestMapping("/api/courses/{courseId}/contents")
@RequiredArgsConstructor
public class CourseContentController {
    private final CourseContentService service;

    @PostMapping
    public ResponseEntity<CourseContentResponse> create(@PathVariable @Positive Long courseId,
            @Valid @RequestBody CourseContentCreateRequest request) {
        CourseContentResponse response = service.create(courseId, request);
        return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/contents/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<CourseContentResponse> getAll(@PathVariable @Positive Long courseId) {
        return service.getAll(courseId);
    }

    @GetMapping("/{contentId}")
    public CourseContentResponse get(@PathVariable @Positive Long courseId, @PathVariable @Positive Long contentId) {
        return service.get(courseId, contentId);
    }

    @PatchMapping("/{contentId}")
    public CourseContentResponse update(@PathVariable @Positive Long courseId, @PathVariable @Positive Long contentId,
            @Valid @RequestBody CourseContentPatchRequest request) {
        return service.update(courseId, contentId, request);
    }

    @DeleteMapping("/{contentId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long courseId, @PathVariable @Positive Long contentId) {
        service.delete(courseId, contentId);
        return ResponseEntity.noContent().build();
    }

    // 부분 목록은 거부하고 전체 콘텐츠를 1..N으로 재정렬
    @PatchMapping("/order")
    public List<CourseContentResponse> reorder(@PathVariable @Positive Long courseId,
            @Valid @RequestBody ContentOrderRequest request) {
        return service.reorder(courseId, request);
    }
}

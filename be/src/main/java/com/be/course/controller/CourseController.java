package com.be.course.controller;

import com.be.course.dto.*;
import com.be.course.service.CourseService;
import com.be.global.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 교육과정 관리 API: 요청 검증 및 DTO 반환만 담당
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService service;

    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseCreateRequest request) {
        CourseResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/courses/" + response.id())).body(response);
    }

    @GetMapping("/{courseId}")
    public CourseResponse get(@PathVariable @Positive Long courseId) {
        return service.get(courseId);
    }

    @GetMapping
    public PageResponse<CourseResponse> search(@Valid @ModelAttribute CourseSearchRequest request) {
        return PageResponse.from(service.search(request));
    }

    @PatchMapping("/{courseId}")
    public CourseResponse update(@PathVariable @Positive Long courseId,
                                 @Valid @RequestBody CoursePatchRequest request) {
        return service.update(courseId, request);
    }

    @PatchMapping("/{courseId}/status")
    public CourseResponse changeStatus(@PathVariable @Positive Long courseId,
                                       @Valid @RequestBody CourseStatusRequest request) {
        return service.changeStatus(courseId, request);
    }
}

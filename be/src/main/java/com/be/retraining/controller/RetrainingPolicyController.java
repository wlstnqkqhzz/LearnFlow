package com.be.retraining.controller;

import com.be.course.dto.CourseResponse;
import com.be.global.dto.PageResponse;
import com.be.retraining.dto.*;
import com.be.retraining.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/retraining-policies")
public class RetrainingPolicyController {
    private final RetrainingPolicyService policies;
    private final RetrainingOccurrenceService occurrences;
    public record StatusRequest(@NotNull Boolean enabled) {}
    public record SkipRequest(@NotNull @Positive Integer occurrenceNumber) {}

    @PostMapping
    public ResponseEntity<RetrainingPolicyResponse> create(@Valid @RequestBody RetrainingPolicyRequest request) {
        var result = policies.create(request);
        return ResponseEntity.created(URI.create("/api/retraining-policies/" + result.id())).body(result);
    }
    @GetMapping
    public PageResponse<RetrainingPolicyResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(policies.list(page, size));
    }
    @GetMapping("/{id}")
    public RetrainingPolicyResponse get(@PathVariable @Positive Long id) { return policies.get(id); }
    @PatchMapping("/{id}")
    public RetrainingPolicyResponse update(@PathVariable @Positive Long id, @Valid @RequestBody RetrainingPolicyRequest request) {
        return policies.update(id, request);
    }
    @PatchMapping("/{id}/status")
    public RetrainingPolicyResponse status(@PathVariable @Positive Long id, @Valid @RequestBody StatusRequest request) {
        return policies.status(id, request.enabled());
    }
    @GetMapping("/{id}/courses")
    public PageResponse<CourseResponse> courses(@PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(policies.occurrences(id, page, size));
    }
    @PutMapping("/{id}/occurrences/{number}")
    public ResponseEntity<CourseResponse> generate(@PathVariable @Positive Long id, @PathVariable @Positive int number) {
        var result = occurrences.generate(id, number);
        return (result.created() ? ResponseEntity.created(URI.create("/api/courses/" + result.course().id())) : ResponseEntity.ok()).body(result.course());
    }
    @PostMapping("/{id}/skip-overdue")
    public RetrainingPolicyResponse skip(@PathVariable @Positive Long id, @Valid @RequestBody SkipRequest request) {
        return occurrences.skip(id, request.occurrenceNumber());
    }
}

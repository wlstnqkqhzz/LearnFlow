package com.be.organization.controller;

import com.be.global.dto.ActivationUpdateRequest;
import com.be.organization.dto.*;
import com.be.organization.service.JobPositionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 직무 REST API - 업무 규칙은 Service에서 처리
@RestController
@RequestMapping("/api/job-positions")
@RequiredArgsConstructor
public class JobPositionController {
    private final JobPositionService service;

    @PostMapping
    public ResponseEntity<JobPositionResponse> create(@Valid @RequestBody JobPositionCreateRequest request) {
        JobPositionResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/job-positions/" + response.id())).body(response);
    }

    // active=true이면 활성 목록, 생략 또는 false이면 전체 목록
    @GetMapping
    public List<JobPositionResponse> getAll(@RequestParam(defaultValue = "false") boolean active) {
        return active ? service.getActive() : service.getAll();
    }

    @GetMapping("/{jobPositionId}")
    public JobPositionResponse get(@PathVariable @Positive Long jobPositionId) {
        return service.get(jobPositionId);
    }

    @PatchMapping("/{jobPositionId}")
    public JobPositionResponse update(@PathVariable @Positive Long jobPositionId,
                                 @Valid @RequestBody JobPositionUpdateRequest request) {
        return service.update(jobPositionId, request);
    }

    @PatchMapping("/{jobPositionId}/status")
    public JobPositionResponse changeStatus(@PathVariable @Positive Long jobPositionId,
                                       @Valid @RequestBody ActivationUpdateRequest request) {
        return request.active() ? service.activate(jobPositionId) : service.deactivate(jobPositionId);
    }
}

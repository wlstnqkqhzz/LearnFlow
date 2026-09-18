package com.be.organization.controller;

import com.be.global.dto.ActivationUpdateRequest;
import com.be.organization.dto.*;
import com.be.organization.service.DepartmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 부서 REST API - 업무 규칙은 Service에서 처리
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService service;

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentCreateRequest request) {
        DepartmentResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/departments/" + response.id())).body(response);
    }

    // active=true이면 활성 목록, 생략 또는 false이면 전체 목록
    @GetMapping
    public List<DepartmentResponse> getAll(@RequestParam(defaultValue = "false") boolean active) {
        return active ? service.getActive() : service.getAll();
    }

    @GetMapping("/{departmentId}")
    public DepartmentResponse get(@PathVariable @Positive Long departmentId) {
        return service.get(departmentId);
    }

    @PatchMapping("/{departmentId}")
    public DepartmentResponse update(@PathVariable @Positive Long departmentId,
                                 @Valid @RequestBody DepartmentPatchRequest request) {
        return service.patch(departmentId, request);
    }

    @PatchMapping("/{departmentId}/status")
    public DepartmentResponse changeStatus(@PathVariable @Positive Long departmentId,
                                       @Valid @RequestBody ActivationUpdateRequest request) {
        return request.active() ? service.activate(departmentId) : service.deactivate(departmentId);
    }
}

package com.be.coursework.controller;
import com.be.coursework.dto.*;
import com.be.coursework.service.AssignmentService;
import com.be.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/courses/{courseId}/assignments")
public class AssignmentController {
    private final AssignmentService service;
    @PostMapping
    public ResponseEntity<AssignmentResponse> create(@PathVariable @Positive Long courseId, @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody AssignmentCreateRequest request) {
        var response = service.create(courseId, principal, request);
        return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/assignments/" + response.assignmentId())).body(response);
    }
    @GetMapping public List<AssignmentResponse> list(@PathVariable @Positive Long courseId, @AuthenticationPrincipal MemberPrincipal principal) {
        return service.list(courseId, principal);
    }
    @GetMapping("/{assignmentId}") public AssignmentResponse get(@PathVariable @Positive Long courseId,
            @PathVariable @Positive Long assignmentId, @AuthenticationPrincipal MemberPrincipal principal) {
        return service.get(courseId, assignmentId, principal);
    }
    @PatchMapping("/{assignmentId}") public AssignmentResponse update(@PathVariable @Positive Long courseId,
            @PathVariable @Positive Long assignmentId, @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody AssignmentPatchRequest request) {
        return service.update(courseId, assignmentId, principal, request);
    }
    @DeleteMapping("/{assignmentId}") public ResponseEntity<Void> delete(@PathVariable @Positive Long courseId,
            @PathVariable @Positive Long assignmentId, @AuthenticationPrincipal MemberPrincipal principal) {
        service.delete(courseId, assignmentId, principal);
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/order") public List<AssignmentResponse> reorder(@PathVariable @Positive Long courseId,
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody AssignmentOrderRequest request) {
        return service.reorder(courseId, principal, request);
    }
}

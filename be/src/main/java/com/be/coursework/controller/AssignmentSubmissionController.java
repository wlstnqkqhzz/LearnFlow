package com.be.coursework.controller;
import com.be.coursework.dto.*;
import com.be.coursework.service.AssignmentSubmissionService;
import com.be.global.dto.PageResponse;
import com.be.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class AssignmentSubmissionController {
    private final AssignmentSubmissionService service;
    @GetMapping("/api/courses/{courseId}/assignment-submissions")
    public PageResponse<SubmissionResponse> search(@PathVariable @Positive Long courseId,
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @ModelAttribute SubmissionSearchRequest request) {
        return PageResponse.from(service.search(courseId, principal, request));
    }
    @GetMapping("/api/assignment-submissions/{submissionId}")
    public SubmissionResponse get(@PathVariable @Positive Long submissionId, @AuthenticationPrincipal MemberPrincipal principal) {
        return service.get(submissionId, principal);
    }
    @PatchMapping("/api/assignment-submissions/{submissionId}/grade")
    public SubmissionResponse grade(@PathVariable @Positive Long submissionId, @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody GradeRequest request) {
        return service.grade(submissionId, principal, request);
    }
    @GetMapping("/api/enrollments/{enrollmentId}/assignments")
    public List<EmployeeAssignmentResponse> mine(@PathVariable @Positive Long enrollmentId, @AuthenticationPrincipal MemberPrincipal principal) {
        return service.mine(enrollmentId, principal);
    }
    @GetMapping("/api/enrollments/{enrollmentId}/assignments/{assignmentId}")
    public EmployeeAssignmentResponse mineOne(@PathVariable @Positive Long enrollmentId, @PathVariable @Positive Long assignmentId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.mineOne(enrollmentId, assignmentId, principal);
    }
    @PutMapping("/api/enrollments/{enrollmentId}/assignments/{assignmentId}/submission")
    public EmployeeAssignmentResponse submit(@PathVariable @Positive Long enrollmentId, @PathVariable @Positive Long assignmentId,
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody SubmissionRequest request) {
        return service.submit(enrollmentId, assignmentId, principal, request);
    }
}

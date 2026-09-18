package com.be.member.controller;

import com.be.global.dto.PageResponse;
import com.be.member.dto.*;
import com.be.member.enums.Role;
import com.be.member.service.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 직원 REST API - 응답은 DTO만 사용
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService service;

    @PostMapping
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberCreateRequest request) {
        MemberResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/members/" + response.id())).body(response);
    }

    @GetMapping("/{memberId}")
    public MemberResponse get(@PathVariable @Positive Long memberId) {
        return service.get(memberId);
    }

    @GetMapping
    public PageResponse<MemberResponse> getAll(@Valid @ModelAttribute MemberSearchRequest request) {
        return PageResponse.from(service.search(request));
    }

    @PatchMapping("/{memberId}")
    public MemberResponse update(@PathVariable @Positive Long memberId,
                                 @Valid @RequestBody MemberPatchRequest request) {
        return service.patch(memberId, request);
    }

    @PatchMapping("/{memberId}/department")
    public MemberResponse changeDepartment(@PathVariable @Positive Long memberId,
                                           @Valid @RequestBody MemberDepartmentUpdateRequest request) {
        return service.changeDepartment(memberId, request);
    }

    @PatchMapping("/{memberId}/job-position")
    public MemberResponse changeJobPosition(@PathVariable @Positive Long memberId,
                                            @Valid @RequestBody MemberJobPositionUpdateRequest request) {
        return service.changeJobPosition(memberId, request);
    }

    @PatchMapping("/{memberId}/status")
    public MemberResponse changeStatus(@PathVariable @Positive Long memberId,
                                       @Valid @RequestBody MemberStatusUpdateRequest request) {
        return service.changeStatus(memberId, request);
    }

    @PostMapping("/{memberId}/roles/{role}")
    public MemberResponse addRole(@PathVariable @Positive Long memberId,
                                  @PathVariable @Pattern(regexp = "INSTRUCTOR|ADMIN") String role) {
        return service.addRole(memberId, new MemberRoleUpdateRequest(Role.valueOf(role)));
    }

    @DeleteMapping("/{memberId}/roles/{role}")
    public MemberResponse removeRole(@PathVariable @Positive Long memberId, @PathVariable Role role) {
        return service.removeRole(memberId, new MemberRoleUpdateRequest(role));
    }
}

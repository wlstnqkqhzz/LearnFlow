package com.be.retraining.service;

import com.be.course.dto.CourseResponse;
import com.be.course.repository.CourseRepository;
import com.be.global.exception.*;
import com.be.retraining.dto.*;
import com.be.retraining.entity.RetrainingPolicy;
import com.be.retraining.repository.RetrainingPolicyRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

@Service @Validated @RequiredArgsConstructor @Transactional(readOnly = true)
public class RetrainingPolicyService {
    private final RetrainingPolicyRepository policies;
    private final CourseRepository courses;

    @Transactional
    public RetrainingPolicyResponse create(@NotNull @Valid RetrainingPolicyRequest r) {
        var source = courses.findByIdForUpdate(r.sourceCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (source.getStartDate() == null) throw new BusinessException(ErrorCode.RETRAINING_SOURCE_INVALID);
        return RetrainingPolicyResponse.from(policies.saveAndFlush(RetrainingPolicy.create(source, r.baseTitle(),
                r.enabled(), r.autoCreate(), r.autoOpen(), r.intervalMonths(), r.firstStartDate(), r.durationDays(), r.generationLeadDays())));
    }

    public RetrainingPolicyResponse get(@NotNull @Positive Long id) {
        return RetrainingPolicyResponse.from(policies.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RETRAINING_POLICY_NOT_FOUND)));
    }

    public Page<RetrainingPolicyResponse> list(@Min(0) int page, @Min(1) @Max(100) int size) {
        return policies.findAll(PageRequest.of(page, size, Sort.by("id"))).map(RetrainingPolicyResponse::from);
    }

    @Transactional
    public RetrainingPolicyResponse update(@NotNull @Positive Long id, @NotNull @Valid RetrainingPolicyRequest r) {
        var policy = locked(id);
        var source = courses.findByIdForUpdate(r.sourceCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (source.getStartDate() == null) throw new BusinessException(ErrorCode.RETRAINING_SOURCE_INVALID);
        policy.configure(source, r.baseTitle(), r.enabled(), r.autoCreate(), r.autoOpen(), r.intervalMonths(),
                r.firstStartDate(), r.durationDays(), r.generationLeadDays());
        policies.flush();
        return RetrainingPolicyResponse.from(policy);
    }

    @Transactional
    public RetrainingPolicyResponse status(@NotNull @Positive Long id, boolean enabled) {
        var policy = locked(id);
        policy.changeEnabled(enabled);
        policies.flush();
        return RetrainingPolicyResponse.from(policy);
    }

    public Page<CourseResponse> occurrences(@NotNull @Positive Long id, @Min(0) int page, @Min(1) @Max(100) int size) {
        if (!policies.existsById(id)) throw new BusinessException(ErrorCode.RETRAINING_POLICY_NOT_FOUND);
        return courses.findByRetrainingPolicyId(id, PageRequest.of(page, size, Sort.by("occurrenceNumber"))).map(CourseResponse::from);
    }

    private RetrainingPolicy locked(Long id) {
        return policies.findForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.RETRAINING_POLICY_NOT_FOUND));
    }
}

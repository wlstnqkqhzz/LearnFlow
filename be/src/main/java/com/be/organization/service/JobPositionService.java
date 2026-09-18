package com.be.organization.service;

import com.be.global.exception.*;
import com.be.organization.dto.*;
import com.be.organization.entity.JobPosition;
import com.be.organization.repository.JobPositionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 직무 생성, 이름 변경, 활성 상태 및 조회 관리
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPositionService {
    private final JobPositionRepository jobPositionRepository;

    @Transactional
    public JobPositionResponse create(@NotNull @Valid JobPositionCreateRequest request) {
        if (jobPositionRepository.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_JOB_POSITION_CODE);
        }
        JobPosition position = JobPosition.create(request.code(), request.name());
        try {
            jobPositionRepository.saveAndFlush(position);
        } catch (DataIntegrityViolationException exception) {
            throw UniqueConstraintErrors.translate(exception);
        }
        return JobPositionResponse.from(position);
    }

    @Transactional
    public JobPositionResponse update(@NotNull @Positive Long id,
                                      @NotNull @Valid JobPositionUpdateRequest request) {
        JobPosition position = find(id);
        position.rename(request.name());
        jobPositionRepository.flush();
        return JobPositionResponse.from(position);
    }

    @Transactional
    public JobPositionResponse activate(@NotNull @Positive Long id) {
        JobPosition position = find(id);
        position.activate();
        jobPositionRepository.flush();
        return JobPositionResponse.from(position);
    }

    @Transactional
    public JobPositionResponse deactivate(@NotNull @Positive Long id) {
        JobPosition position = find(id);
        position.deactivate();
        jobPositionRepository.flush();
        return JobPositionResponse.from(position);
    }

    public JobPositionResponse get(@NotNull @Positive Long id) {
        return JobPositionResponse.from(find(id));
    }

    public List<JobPositionResponse> getAll() {
        return jobPositionRepository.findAll(Sort.by("id")).stream()
                .map(JobPositionResponse::from).toList();
    }

    public List<JobPositionResponse> getActive() {
        return jobPositionRepository.findByIsActiveTrue().stream()
                .map(JobPositionResponse::from).toList();
    }

    private JobPosition find(Long id) {
        return jobPositionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSITION_NOT_FOUND));
    }
}

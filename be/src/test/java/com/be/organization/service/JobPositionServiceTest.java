package com.be.organization.service;

import com.be.global.exception.*;
import com.be.organization.dto.*;
import com.be.organization.entity.JobPosition;
import com.be.organization.repository.JobPositionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 직무의 코드 불변성과 활성 상태 검증
@ExtendWith(MockitoExtension.class)
class JobPositionServiceTest {
    @Mock JobPositionRepository repository;
    JobPositionService service;

    @BeforeEach
    void setUp() {
        service = new JobPositionService(repository);
    }

    @Test
    void createsPosition() {
        var response = service.create(new JobPositionCreateRequest(" BACKEND ", " 개발자 "));
        assertThat(response.code()).isEqualTo("BACKEND");
        assertThat(response.name()).isEqualTo("개발자");
        assertThat(response.isActive()).isTrue();
        verify(repository).saveAndFlush(any(JobPosition.class));
    }

    @Test
    void rejectsDuplicateCode() {
        when(repository.existsByCode("BACKEND")).thenReturn(true);
        assertCode(ErrorCode.DUPLICATE_JOB_POSITION_CODE,
                () -> service.create(new JobPositionCreateRequest("BACKEND", "개발자")));
    }

    @Test
    void activatesAndDeactivates() {
        JobPosition position = existing();
        assertThat(service.deactivate(1L).isActive()).isFalse();
        assertThat(service.activate(1L).isActive()).isTrue();
        assertThat(position.isActive()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void renamesWithoutChangingCode() {
        JobPosition position = existing();
        assertThat(service.update(1L, new JobPositionUpdateRequest("새 직무명")).name())
                .isEqualTo("새 직무명");
        assertThat(position.getCode()).isEqualTo("BACKEND");
    }

    @Test
    void readsDtoAndActiveList() {
        JobPosition position = existing();
        when(repository.findByIsActiveTrue()).thenReturn(List.of(position));
        assertThat(service.get(1L).id()).isEqualTo(1L);
        assertThat(service.getActive()).hasSize(1);
    }

    @Test
    void rejectsMissingPosition() {
        assertCode(ErrorCode.JOB_POSITION_NOT_FOUND, () -> service.get(99L));
    }

    private JobPosition existing() {
        JobPosition position = JobPosition.create("BACKEND", "개발자");
        ReflectionTestUtils.setField(position, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(position));
        return position;
    }

    private void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}

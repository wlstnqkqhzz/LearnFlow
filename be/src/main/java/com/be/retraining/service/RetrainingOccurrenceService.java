package com.be.retraining.service;

import com.be.course.dto.*;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.course.service.CourseService;
import com.be.global.exception.*;
import com.be.retraining.dto.RetrainingPolicyResponse;
import com.be.retraining.entity.RetrainingPolicy;
import com.be.retraining.repository.RetrainingPolicyRepository;
import jakarta.validation.constraints.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

@Service @Validated @RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class RetrainingOccurrenceService {
    private final RetrainingPolicyRepository policies;
    private final CourseRepository courses;
    private final RetrainingCourseCopier copier;
    private final CourseService courseService;
    private final Clock clock;
    public record Result(CourseResponse course, boolean created) {}

    public Result generate(@NotNull @Positive Long id, @Positive int number) {
        return generateLocked(locked(id), number, false, today());
    }

    // 배치 조회 이후 수동 생성/중지가 먼저 커밋되었을 수 있으므로 잠금 안에서 다시 판단한다.
    public Result generateAutomatically(@NotNull @Positive Long id, @NotNull LocalDate today) {
        var policy = locked(id);
        if (!policy.isEnabled() || !policy.isAutoCreate() || policy.getNextGenerationDate().isAfter(today)) return null;
        return generateLocked(policy, policy.getNextOccurrenceNumber(), true, today);
    }

    private Result generateLocked(RetrainingPolicy policy, int number, boolean automatic, LocalDate today) {
        var existing = courses.findByRetrainingPolicyIdAndOccurrenceNumber(policy.getId(), number);
        if (existing.isPresent()) return new Result(CourseResponse.from(existing.get()), false);
        if (number != policy.getNextOccurrenceNumber()) throw new BusinessException(ErrorCode.INVALID_RETRAINING_OCCURRENCE);
        if (!policy.isEnabled()) throw new BusinessException(ErrorCode.RETRAINING_DISABLED);
        if (automatic && courses.existsByRetrainingPolicyIdAndStatus(policy.getId(), CourseStatus.DRAFT))
            throw new BusinessException(ErrorCode.RETRAINING_DRAFT_PENDING);
        var source = courses.findByIdForUpdate(policy.getSourceCourse().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (copier.overdue(source, policy.dates(number), today)) throw new BusinessException(ErrorCode.RETRAINING_OVERDUE);
        var course = copier.copy(policy, source, number);
        if (policy.isAutoOpen()) courseService.changeStatus(course.getId(), new CourseStatusRequest(CourseStatus.OPEN));
        policy.advance();
        policies.flush();
        return new Result(CourseResponse.from(course), true);
    }

    // 한 요청은 지정 회차 하나만 skip한다. 응답 유실 후 재요청해도 다음 회차까지 건너뛰지 않는다.
    public RetrainingPolicyResponse skip(@NotNull @Positive Long id, @Positive int number) {
        var policy = locked(id);
        if (courses.findByRetrainingPolicyIdAndOccurrenceNumber(id, number).isPresent())
            throw new BusinessException(ErrorCode.INVALID_RETRAINING_OCCURRENCE);
        if (number < policy.getNextOccurrenceNumber()) return RetrainingPolicyResponse.from(policy);
        if (number != policy.getNextOccurrenceNumber()) throw new BusinessException(ErrorCode.INVALID_RETRAINING_OCCURRENCE);
        var source = courses.findByIdForUpdate(policy.getSourceCourse().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (!copier.overdue(source, policy.dates(number), today())) throw new BusinessException(ErrorCode.RETRAINING_NOT_OVERDUE);
        policy.advance();
        policies.flush();
        return RetrainingPolicyResponse.from(policy);
    }

    private RetrainingPolicy locked(Long id) {
        return policies.findForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.RETRAINING_POLICY_NOT_FOUND));
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))); }
}

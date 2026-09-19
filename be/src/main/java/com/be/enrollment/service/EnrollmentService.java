package com.be.enrollment.service;

import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.enrollment.dto.*;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.global.exception.*;
import com.be.member.enums.MemberStatus;
import com.be.member.repository.MemberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 수동 배정 및 관리/본인 조회: 학습 상태 전이와 삭제는 제공하지 않음
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final MemberRepository members;
    private final Clock clock;

    @Transactional
    public EnrollmentResponse assignManually(@NotNull @Positive Long courseId,
                                             @NotNull @Valid ManualEnrollmentRequest request) {
        var course = courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (course.getStatus() != CourseStatus.OPEN) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OPEN_FOR_ASSIGNMENT);
        }
        var member = members.findByIdForUpdate(request.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus() == MemberStatus.RESIGNED) {
            throw new BusinessException(ErrorCode.RESIGNED_MEMBER_ASSIGNMENT);
        }
        if (enrollments.findExistingForAssignment(member.getId(), courseId).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT);
        }
        var enrollment = Enrollment.manual(member, course, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        try {
            // 기존 @Version Long=0 매핑에서는 merge 경로일 수 있으므로 반환된 관리 엔티티 사용
            enrollment = enrollments.saveAndFlush(enrollment);
        } catch (DataIntegrityViolationException exception) {
            throw UniqueConstraintErrors.translate(exception);
        }
        return EnrollmentResponse.from(enrollment);
    }

    public EnrollmentResponse get(@NotNull @Positive Long id) {
        return EnrollmentResponse.from(enrollments.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND)));
    }

    public Page<EnrollmentResponse> forCourse(@NotNull @Positive Long courseId,
                                              @NotNull @Valid EnrollmentSearchRequest request) {
        if (!courses.existsById(courseId)) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        return search(courseId, null, request);
    }

    // Controller가 인증된 MemberPrincipal의 ID만 전달
    public Page<EnrollmentResponse> forMember(@NotNull @Positive Long memberId,
                                              @NotNull @Valid EnrollmentSearchRequest request) {
        return search(null, memberId, request);
    }

    private Page<EnrollmentResponse> search(Long courseId, Long memberId, EnrollmentSearchRequest request) {
        return enrollments.search(courseId, memberId, request.status(),
                PageRequest.of(request.page(), request.size(), Sort.by("id"))).map(EnrollmentResponse::from);
    }
}

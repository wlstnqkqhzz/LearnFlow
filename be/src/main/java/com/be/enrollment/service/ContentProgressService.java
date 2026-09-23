package com.be.enrollment.service;

import com.be.course.repository.CourseContentRepository;
import com.be.enrollment.dto.*;
import com.be.enrollment.entity.*;
import com.be.enrollment.repository.*;
import com.be.global.exception.*;
import com.be.global.security.MemberPrincipal;
import com.be.member.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 콘텐츠 진도와 수강 시작·수료를 하나의 트랜잭션으로 처리
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentProgressService {
    private final EnrollmentRepository enrollments;
    private final CourseContentRepository contents;
    private final ContentProgressRepository progresses;
    private final EnrollmentCompletionService completion;
    private final Clock clock;
    private final com.be.exam.repository.ExamRepository exams;

    // 관리자는 조회만 예외 허용; 직원은 항상 본인 수강인지 확인
    public EnrollmentProgressResponse get(Long enrollmentId, MemberPrincipal principal) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        requireAccess(enrollment, principal, true);
        var items = contentResponses(enrollment);
        return response(enrollment, items, completion.summarize(items, enrollment.getCourse().getPassingProgressRate()));
    }

    // 최초 요청은 생성, 이후 요청은 동일 UNIQUE 키의 row 갱신; 충돌을 자동 덮어쓰지 않음
    @Transactional
    public EnrollmentProgressResponse update(Long enrollmentId, Long contentId, MemberPrincipal principal,
            @NotNull @Valid ContentProgressUpdateRequest request) {
        Enrollment enrollment = enrollments.findForProgressUpdate(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        requireAccess(enrollment, principal, false);
        enrollment.requireProgressEditable();
        var content = contents.findByIdAndCourseId(contentId, enrollment.getCourse().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_CONTENT_NOT_FOUND));
        var progress = progresses.findByEnrollmentIdAndCourseContentId(enrollmentId, contentId)
                .orElseGet(() -> ContentProgress.create(enrollment, content));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        progress.updateProgress(request.progressRate(), now);
        if (request.progressRate().signum() > 0) enrollment.startLearning(now);
        // version 초기값 0L인 신규 엔티티는 merge될 수 있으므로 flush 후 관리 상태로 다시 조회
        progresses.saveAndFlush(progress);
        var items = contentResponses(enrollment);
        var summary = completion.summarize(items, enrollment.getCourse().getPassingProgressRate());
        completion.evaluate(enrollment, summary, now);
        enrollments.flush();
        return response(enrollment, items, summary);
    }

    private void requireAccess(Enrollment enrollment, MemberPrincipal principal, boolean allowAdmin) {
        if (principal != null && allowAdmin && principal.roles().contains(Role.ADMIN)) return;
        if (principal == null || !principal.roles().contains(Role.EMPLOYEE)
                || !Objects.equals(enrollment.getMember().getId(), principal.memberId())) {
            throw new BusinessException(ErrorCode.ENROLLMENT_PROGRESS_ACCESS_DENIED);
        }
    }

    // 콘텐츠 일괄 조회 + 진도 일괄 조회로 누락 없는 응답 구성 (콘텐츠별 추가 쿼리 없음)
    private List<ContentProgressResponse> contentResponses(Enrollment enrollment) {
        var courseContents = contents.findByCourseIdOrderBySortOrderAsc(enrollment.getCourse().getId());
        var progressByContent = progresses.findByEnrollmentId(enrollment.getId()).stream()
                .collect(Collectors.toMap(p -> p.getCourseContent().getId(), Function.identity()));
        return courseContents.stream().map(c -> ContentProgressResponse.from(c, progressByContent.get(c.getId()))).toList();
    }

    private EnrollmentProgressResponse response(Enrollment enrollment, List<ContentProgressResponse> items,
            EnrollmentCompletionService.ProgressSummary summary) {
        var course = enrollment.getCourse();
        var instructor = course.getInstructor();
        return new EnrollmentProgressResponse(enrollment.getId(), enrollment.getStatus(), enrollment.getDueDate(),
                enrollment.getAssignedAt(), enrollment.getStartedAt(), enrollment.getCompletedAt(),
                course.getId(), course.getTitle(), course.getDescription(), course.getCourseType(),
                course.getStartDate(), course.getEndDate(),
                instructor == null ? null : instructor.getId(), instructor == null ? null : instructor.getName(),
                summary.progressRate(), course.getPassingProgressRate(), summary.contentConditionSatisfied(), items,
                exams.findByCourseId(course.getId()).map(EnrollmentExamResponse::from).orElse(null));
    }
}

package com.be.coursework.service;

import com.be.course.repository.CourseRepository;
import com.be.coursework.dto.*;
import com.be.coursework.entity.*;
import com.be.coursework.enums.*;
import com.be.coursework.repository.*;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.enrollment.service.EnrollmentCompletionService;
import com.be.global.exception.*;
import com.be.global.security.MemberPrincipal;
import com.be.member.repository.MemberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

@Service @Validated @RequiredArgsConstructor @Transactional(readOnly = true)
public class AssignmentSubmissionService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final MemberRepository members;
    private final CourseworkAccess access;
    private final EnrollmentCompletionService completion;
    private final Clock clock;

    public List<EmployeeAssignmentResponse> mine(@NotNull @Positive Long enrollmentId, MemberPrincipal principal) {
        var e = enrollment(enrollmentId, false);
        access.own(e, principal);
        var byAssignment = submissions.findByEnrollmentId(enrollmentId).stream()
            .collect(Collectors.toMap(s -> s.getAssignment().getId(), Function.identity()));
        var today = today();
        return assignments.findByCourseIdOrderBySortOrderAsc(e.getCourse().getId()).stream()
            .map(a -> employeeResponse(a, e, byAssignment.get(a.getId()), today)).toList();
    }
    public EmployeeAssignmentResponse mineOne(@NotNull @Positive Long enrollmentId, @NotNull @Positive Long assignmentId,
            MemberPrincipal principal) {
        return mine(enrollmentId, principal).stream().filter(a -> a.assignment().assignmentId().equals(assignmentId))
            .findFirst().orElseThrow(() -> error(ErrorCode.ASSIGNMENT_NOT_FOUND));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeAssignmentResponse submit(@NotNull @Positive Long enrollmentId, @NotNull @Positive Long assignmentId,
            MemberPrincipal principal, @NotNull @Valid SubmissionRequest request) {
        var e = enrollment(enrollmentId, true);
        access.own(e, principal);
        if (!editable(e)) throw error(ErrorCode.ASSIGNMENT_SUBMISSION_NOT_EDITABLE);
        var a = assignments.findByIdAndCourseId(assignmentId, e.getCourse().getId())
            .orElseThrow(() -> error(ErrorCode.ASSIGNMENT_NOT_FOUND));
        var instant = clock.instant();
        var today = instant.atZone(SEOUL).toLocalDate();
        if (today.isAfter(effectiveDueDate(a, e))) throw error(ErrorCode.ASSIGNMENT_DEADLINE_PASSED);
        String content = validateContent(request.submissionType(), request.content());
        var now = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        var s = submissions.findForUpdate(assignmentId, enrollmentId).orElse(null);
        if (s == null) s = AssignmentSubmission.create(a, e, request.submissionType(), content, now);
        else s.resubmit(request.submissionType(), content, now);
        e.startLearning(now);
        s = submissions.saveAndFlush(s);
        enrollments.flush();
        return employeeResponse(a, e, s, today);
    }
    public Page<SubmissionResponse> search(@NotNull @Positive Long courseId, MemberPrincipal principal,
            @NotNull @Valid SubmissionSearchRequest request) {
        var course = courses.findById(courseId).orElseThrow(() -> error(ErrorCode.COURSE_NOT_FOUND));
        access.manage(course, principal);
        return submissions.search(courseId, request.assignmentId(), request.enrollmentId(), request.memberId(),
            request.status() == null ? null : request.status().name(),
            PageRequest.of(request.page(), request.size(), Sort.by("submittedAt", "id").descending())).map(SubmissionResponse::from);
    }
    public SubmissionResponse get(@NotNull @Positive Long submissionId, MemberPrincipal principal) {
        var s = submissions.findById(submissionId).orElseThrow(() -> error(ErrorCode.ASSIGNMENT_SUBMISSION_NOT_FOUND));
        access.manage(s.getAssignment().getCourse(), principal);
        return SubmissionResponse.from(s);
    }
    // Enrollment 버전 증가로 콘텐츠/시험/만료 처리와의 경쟁도 감지한다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SubmissionResponse grade(@NotNull @Positive Long submissionId, MemberPrincipal principal,
            @NotNull @Valid GradeRequest request) {
        var enrollmentId = submissions.findEnrollmentId(submissionId)
            .orElseThrow(() -> error(ErrorCode.ASSIGNMENT_SUBMISSION_NOT_FOUND));
        var e = enrollment(enrollmentId, true);
        access.manage(e.getCourse(), principal);
        var s = submissions.findForGrading(submissionId)
            .orElseThrow(() -> error(ErrorCode.ASSIGNMENT_SUBMISSION_NOT_FOUND));
        if (!Objects.equals(s.getAssignment().getCourse().getId(), e.getCourse().getId()))
            throw error(ErrorCode.ASSIGNMENT_NOT_FOUND);
        var grader = members.findById(principal.memberId()).orElseThrow(() -> error(ErrorCode.MEMBER_NOT_FOUND));
        var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        s.grade(request.score(), request.feedback(), grader, now);
        submissions.flush();
        if (Boolean.TRUE.equals(s.getPassed())) completion.evaluate(e, now);
        enrollments.flush();
        return SubmissionResponse.from(s);
    }
    private Enrollment enrollment(Long id, boolean write) {
        return (write ? enrollments.findForCourseworkUpdate(id) : enrollments.findById(id))
            .orElseThrow(() -> error(ErrorCode.ENROLLMENT_NOT_FOUND));
    }
    private boolean editable(Enrollment e) {
        return e.getStatus() == EnrollmentStatus.ASSIGNED || e.getStatus() == EnrollmentStatus.IN_PROGRESS;
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(SEOUL)); }
    private LocalDate effectiveDueDate(Assignment a, Enrollment e) {
        return a.getDueDate().isBefore(e.getDueDate()) ? a.getDueDate() : e.getDueDate();
    }
    private EmployeeAssignmentResponse employeeResponse(Assignment a, Enrollment e, AssignmentSubmission s, LocalDate today) {
        var due = effectiveDueDate(a, e);
        boolean allowed = editable(e) && !today.isAfter(due) && (s == null || !Boolean.TRUE.equals(s.getPassed()));
        return new EmployeeAssignmentResponse(AssignmentResponse.from(a),
            s == null ? AssignmentSubmissionStatus.NOT_SUBMITTED : s.status(), due, allowed,
            allowed && s != null, s == null ? null : SubmissionResponse.from(s));
    }
    static String validateContent(AssignmentSubmissionType type, String raw) {
        if (type == null || raw == null) throw error(ErrorCode.INVALID_ASSIGNMENT_CONTENT);
        var content = raw.trim();
        if (content.isBlank() || content.length() > 10000) throw error(ErrorCode.INVALID_ASSIGNMENT_CONTENT);
        if (type == AssignmentSubmissionType.URL) {
            try {
                var uri = new URI(content);
                if (content.length() > 2048 || !uri.isAbsolute() || uri.getHost() == null
                    || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())))
                    throw error(ErrorCode.INVALID_ASSIGNMENT_CONTENT);
            } catch (java.net.URISyntaxException ex) { throw error(ErrorCode.INVALID_ASSIGNMENT_CONTENT); }
        }
        return content;
    }
    private static BusinessException error(ErrorCode code) { return new BusinessException(code); }
}

package com.be.exam.service;

import com.be.course.repository.CourseRepository;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.enrollment.service.EnrollmentCompletionService;
import com.be.exam.dto.*;
import com.be.exam.entity.*;
import com.be.exam.enums.QuestionType;
import com.be.exam.repository.*;
import com.be.global.exception.*;
import com.be.global.security.MemberPrincipal;
import com.be.member.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

// 응시 시작·답안·제출을 수강 행 잠금 아래 처리; 제출 전체가 하나의 트랜잭션
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExamAttemptService {
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final ExamRepository exams;
    private final ExamAttemptRepository attempts;
    private final ExamAnswerRepository answers;
    private final QuestionRepository questions;
    private final QuestionChoiceRepository choices;
    private final ExamConfigurationValidator validator;
    private final ExamGradingService grading;
    private final EnrollmentCompletionService completion;
    private final Clock clock;

    // Course → Enrollment 순서로 잠가 최초 응시와 관리자 구성 변경의 경쟁을 방지
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StartResult start(@NotNull @Positive Long enrollmentId, MemberPrincipal principal) {
        Long courseId = enrollments.findCourseId(enrollmentId).orElseThrow(() -> error(ErrorCode.ENROLLMENT_NOT_FOUND));
        courses.findByIdForUpdate(courseId).orElseThrow(() -> error(ErrorCode.COURSE_NOT_FOUND));
        Enrollment enrollment = lockedEnrollment(enrollmentId);
        access(enrollment, principal, false);
        editable(enrollment);
        Exam exam = exams.findByCourseId(enrollment.getCourse().getId()).orElseThrow(() -> error(ErrorCode.EXAM_NOT_FOUND));
        validate(exam);
        var history = attempts.findByEnrollmentIdOrderByAttemptNumberAsc(enrollmentId);
        if (passed(history)) throw error(ErrorCode.EXAM_ALREADY_PASSED);
        var open = history.stream().filter(a -> a.getSubmittedAt() == null).findFirst();
        if (open.isPresent()) return new StartResult(AttemptResponse.from(open.get(), history.size()), false);
        int last = history.stream().mapToInt(ExamAttempt::getAttemptNumber).max().orElse(0);
        if (last >= exam.getMaxAttempts() || history.size() >= exam.getMaxAttempts()) throw error(ErrorCode.EXAM_ATTEMPTS_EXHAUSTED);
        var now = now();
        enrollment.startLearning(now);
        var attempt = attempts.saveAndFlush(ExamAttempt.start(enrollment, exam, last + 1, now));
        return new StartResult(AttemptResponse.from(attempt, history.size() + 1), true);
    }

    // 문제 응답은 본인에게만 제공; 관리자는 관리용 문제 API 사용
    public AttemptPaperResponse paper(@NotNull @Positive Long attemptId, MemberPrincipal principal) {
        var attempt = get(attemptId);
        access(attempt.getEnrollment(), principal, false);
        var items = questions.findByExamIdOrderBySortOrderAsc(attempt.getExam().getId());
        var grouped = choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(attempt.getExam().getId()).stream()
                .collect(Collectors.groupingBy(c -> c.getQuestion().getId()));
        var saved = answers.findByExamAttemptId(attemptId).stream().collect(Collectors.toMap(a -> a.getQuestion().getId(),
                a -> a.getSelectedChoices().stream().map(QuestionChoice::getId).sorted().toList()));
        return new AttemptPaperResponse(attemptId, attempt.getExam().getTitle(), items.stream().map(q ->
                new AttemptPaperResponse.QuestionItem(q.getId(), q.getQuestionType(), q.getQuestionText(), q.getScore(), q.getSortOrder(),
                        grouped.getOrDefault(q.getId(), List.of()).stream().sorted(Comparator.comparingInt(QuestionChoice::getSortOrder))
                                .map(c -> new AttemptPaperResponse.ChoiceItem(c.getId(), c.getChoiceText(), c.getSortOrder())).toList(),
                        saved.getOrDefault(q.getId(), List.of()))).toList());
    }

    // 수강 → 응시 순서로 직렬화; 제출 전에만 기존 답안의 선택 집합 교체
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AnswerResponse saveAnswer(@NotNull @Positive Long attemptId, @NotNull @Positive Long questionId,
            MemberPrincipal principal, @NotNull @Valid AnswerSaveRequest request) {
        var attempt = lockedAttempt(attemptId, principal);
        attempt.requireUnsubmitted();
        editable(attempt.getEnrollment());
        var question = questions.findByIdAndExamId(questionId, attempt.getExam().getId())
                .orElseThrow(() -> error(ErrorCode.QUESTION_NOT_FOUND));
        Set<Long> ids = new HashSet<>(request.selectedChoiceIds());
        if (ids.size() != request.selectedChoiceIds().size()
                || (question.getQuestionType() != QuestionType.MULTIPLE_CHOICE && ids.size() != 1)) {
            throw error(ErrorCode.INVALID_EXAM_ANSWER);
        }
        var available = choices.findByQuestionIdOrderBySortOrderAsc(questionId);
        var selected = available.stream().filter(c -> ids.contains(c.getId())).collect(Collectors.toSet());
        if (selected.size() != ids.size()) throw error(ErrorCode.INVALID_EXAM_ANSWER);
        var answer = answers.findByExamAttemptIdAndQuestionId(attemptId, questionId)
                .orElseGet(() -> ExamAnswer.create(attempt, question));
        answer.replaceChoices(selected);
        answers.saveAndFlush(answer);
        return new AnswerResponse(questionId, ids.stream().sorted().toList());
    }

    // 미응답을 포함한 전체 시험 채점과 수강 상태 확정을 원자적으로 처리
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttemptResponse submit(@NotNull @Positive Long attemptId, MemberPrincipal principal) {
        var attempt = lockedAttempt(attemptId, principal);
        attempt.requireUnsubmitted();
        var enrollment = attempt.getEnrollment();
        editable(enrollment);
        var exam = attempt.getExam();
        var items = questions.findByExamIdOrderBySortOrderAsc(exam.getId());
        var options = choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(exam.getId());
        validator.validate(exam, items, options);
        var score = grading.grade(items, options, answers.findByExamAttemptId(attemptId));
        boolean success = score.compareTo(exam.getPassingScore()) >= 0;
        var now = now();
        attempt.submit(score, success, now);
        attempts.flush(); // 합격 존재 조회와 제출 횟수 계산에서 현재 제출까지 포함
        var history = attempts.findByEnrollmentIdOrderByAttemptNumberAsc(enrollment.getId());
        if (success) completion.evaluate(enrollment, now);
        else if (!passed(history) && history.stream().filter(a -> a.getSubmittedAt() != null).count() >= exam.getMaxAttempts()) {
            enrollment.failLearning();
        }
        enrollments.flush();
        return AttemptResponse.from(attempt, history.size());
    }

    public AttemptResponse result(@NotNull @Positive Long attemptId, MemberPrincipal principal) {
        var attempt = get(attemptId);
        access(attempt.getEnrollment(), principal, true);
        if (attempt.getSubmittedAt() == null) throw error(ErrorCode.EXAM_ATTEMPT_NOT_SUBMITTED);
        return AttemptResponse.from(attempt, attempts.findByEnrollmentIdOrderByAttemptNumberAsc(attempt.getEnrollment().getId()).size());
    }

    public List<AttemptResponse> history(@NotNull @Positive Long enrollmentId, MemberPrincipal principal) {
        var enrollment = enrollments.findById(enrollmentId).orElseThrow(() -> error(ErrorCode.ENROLLMENT_NOT_FOUND));
        access(enrollment, principal, true);
        var history = attempts.findByEnrollmentIdOrderByAttemptNumberAsc(enrollmentId);
        return history.stream().map(a -> AttemptResponse.from(a, history.size())).toList();
    }

    private ExamAttempt lockedAttempt(Long id, MemberPrincipal principal) {
        var enrollmentId = attempts.findEnrollmentId(id).orElseThrow(() -> error(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));
        var enrollment = lockedEnrollment(enrollmentId);
        access(enrollment, principal, false);
        return attempts.findForUpdate(id).orElseThrow(() -> error(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));
    }
    private Enrollment lockedEnrollment(Long id) {
        return enrollments.findForExamUpdate(id).orElseThrow(() -> error(ErrorCode.ENROLLMENT_NOT_FOUND));
    }
    private ExamAttempt get(Long id) { return attempts.findById(id).orElseThrow(() -> error(ErrorCode.EXAM_ATTEMPT_NOT_FOUND)); }
    private void validate(Exam exam) {
        validator.validate(exam, questions.findByExamIdOrderBySortOrderAsc(exam.getId()),
                choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(exam.getId()));
    }
    private boolean passed(List<ExamAttempt> history) {
        return history.stream().anyMatch(a -> a.getSubmittedAt() != null && Boolean.TRUE.equals(a.getPassed()));
    }
    private void editable(Enrollment enrollment) {
        if (enrollment.getStatus() != EnrollmentStatus.ASSIGNED && enrollment.getStatus() != EnrollmentStatus.IN_PROGRESS)
            throw error(ErrorCode.ENROLLMENT_EXAM_NOT_EDITABLE);
    }
    private void access(Enrollment enrollment, MemberPrincipal principal, boolean adminRead) {
        if (principal != null && adminRead && principal.roles().contains(Role.ADMIN)) return;
        if (principal == null || !principal.roles().contains(Role.EMPLOYEE)
                || !Objects.equals(principal.memberId(), enrollment.getMember().getId())) throw error(ErrorCode.EXAM_ACCESS_DENIED);
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private BusinessException error(ErrorCode code) { return new BusinessException(code); }
    public record StartResult(AttemptResponse attempt, boolean created) {}
}

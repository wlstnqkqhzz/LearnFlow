package com.be.exam.service;

import com.be.course.repository.CourseRepository;
import com.be.exam.dto.*;
import com.be.exam.entity.Exam;
import com.be.exam.repository.*;
import com.be.global.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 모든 시험 관리 쓰기는 부모 Course 잠금부터 획득하여 구성 변경을 직렬화
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExamService {
    private final CourseRepository courses;
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final QuestionChoiceRepository choices;
    private final ExamConfigurationValidator validator;
    private final ExamAttemptRepository attempts;

    @Transactional
    public ExamResponse create(@NotNull @Positive Long courseId, @NotNull @Valid ExamCreateRequest request) {
        var course = courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (exams.existsByCourseId(courseId)) throw new BusinessException(ErrorCode.DUPLICATE_EXAM);
        var exam = exams.saveAndFlush(Exam.create(course, request.title().trim(), request.passingScore(), request.maxAttempts()));
        return ExamResponse.from(exam, java.util.List.of());
    }

    public ExamResponse get(@NotNull @Positive Long courseId) {
        return response(requireExam(courseId, false));
    }

    @Transactional
    public ExamResponse update(@NotNull @Positive Long courseId, @NotNull @Valid ExamPatchRequest request) {
        Exam exam = requireExam(courseId, true);
        requireMutable(exam);
        exam.update(request.getTitle() == null ? exam.getTitle() : request.getTitle().trim(),
                request.getPassingScore() == null ? exam.getPassingScore() : request.getPassingScore(),
                request.getMaxAttempts() == null ? exam.getMaxAttempts() : request.getMaxAttempts());
        exams.flush();
        return response(exam);
    }

    // 성공 시 시험 요약 반환, 무효 구성은 기존 업무 오류 형식의 400
    public ExamResponse validateConfiguration(@NotNull @Positive Long courseId) {
        Exam exam = requireExam(courseId, false);
        var items = questions.findByExamIdOrderBySortOrderAsc(exam.getId());
        validator.validate(exam, items, choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(exam.getId()));
        return ExamResponse.from(exam, items);
    }

    // QuestionService도 동일한 Course → Exam 검증 및 잠금 순서를 사용
    Exam requireExam(Long courseId, boolean write) {
        if (write) {
            courses.findByIdForUpdate(courseId).orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        } else if (!courses.existsById(courseId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        return exams.findByCourseId(courseId).orElseThrow(() -> new BusinessException(ErrorCode.EXAM_NOT_FOUND));
    }

    private ExamResponse response(Exam exam) {
        return ExamResponse.from(exam, questions.findByExamIdOrderBySortOrderAsc(exam.getId()));
    }

    // 스냅샷이 없으므로 문구·순서를 포함한 모든 구성 변경을 첫 응시 후 동결
    void requireMutable(Exam exam) {
        if (attempts.existsByExamId(exam.getId())) throw new BusinessException(ErrorCode.EXAM_CONFIGURATION_LOCKED);
    }
}

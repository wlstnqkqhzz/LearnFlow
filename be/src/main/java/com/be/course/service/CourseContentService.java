package com.be.course.service;

import com.be.course.dto.*;
import com.be.course.entity.*;
import com.be.course.repository.*;
import com.be.global.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 모든 쓰기는 부모 과정 행을 잠가 콘텐츠 추가·삭제·순서 변경을 직렬화
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseContentService {
    private final CourseRepository courses;
    private final CourseContentRepository contents;

    @Transactional
    public CourseContentResponse create(@NotNull @Positive Long courseId,
                                       @NotNull @Valid CourseContentCreateRequest request) {
        Course course = locked(courseId);
        if (contents.existsByCourseIdAndSortOrder(courseId, request.sortOrder())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CONTENT_SORT_ORDER);
        }
        CourseContent content = CourseContent.create(course, request.title(), request.contentType(),
                request.contentUrl(), request.durationSeconds(), request.sortOrder(), request.required());
        try {
            contents.saveAndFlush(content);
        } catch (DataIntegrityViolationException exception) {
            throw UniqueConstraintErrors.translate(exception);
        }
        return CourseContentResponse.from(content);
    }

    public List<CourseContentResponse> getAll(@NotNull @Positive Long courseId) {
        if (!courses.existsById(courseId)) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        return contents.findByCourseIdOrderBySortOrderAsc(courseId).stream().map(CourseContentResponse::from).toList();
    }

    public CourseContentResponse get(@NotNull @Positive Long courseId, @NotNull @Positive Long contentId) {
        return CourseContentResponse.from(find(courseId, contentId));
    }

    @Transactional
    public CourseContentResponse update(@NotNull @Positive Long courseId, @NotNull @Positive Long contentId,
                                       @NotNull @Valid CourseContentPatchRequest request) {
        locked(courseId);
        CourseContent content = find(courseId, contentId);
        int order = request.getSortOrder() == null ? content.getSortOrder() : request.getSortOrder();
        if (contents.existsByCourseIdAndSortOrderAndIdNot(courseId, order, contentId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_CONTENT_SORT_ORDER);
        }
        content.update(request.getTitle() == null ? content.getTitle() : request.getTitle(),
                request.getContentType() == null ? content.getContentType() : request.getContentType(),
                request.getContentUrl() == null ? content.getContentUrl() : request.getContentUrl(),
                request.isDurationSecondsPresent() ? request.getDurationSeconds() : content.getDurationSeconds(),
                order, request.getRequired() == null ? content.isRequired() : request.getRequired());
        flush();
        return CourseContentResponse.from(content);
    }

    // FK RESTRICT는 유지: 참조 중인 콘텐츠 삭제는 기존 DATA_CONFLICT(409)로 처리
    @Transactional
    public void delete(@NotNull @Positive Long courseId, @NotNull @Positive Long contentId) {
        locked(courseId);
        contents.delete(find(courseId, contentId));
        contents.flush();
    }

    // 전체 콘텐츠만 재정렬: 임시 양수 영역으로 이동 후 1..N 순서 부여
    @Transactional
    public List<CourseContentResponse> reorder(@NotNull @Positive Long courseId,
                                               @NotNull @Valid ContentOrderRequest request) {
        locked(courseId);
        List<CourseContent> current = contents.findByCourseIdOrderBySortOrderAsc(courseId);
        Map<Long, CourseContent> byId = current.stream().collect(Collectors.toMap(CourseContent::getId, c -> c));
        List<Long> ids = request.contentIds();
        if (ids.size() != byId.size() || new HashSet<>(ids).size() != ids.size()
                || !byId.keySet().equals(new HashSet<>(ids))) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_ORDER);
        }
        int max = current.stream().mapToInt(CourseContent::getSortOrder).max().orElse(0);
        if ((long) max + ids.size() > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.CONTENT_ORDER_LIMIT_EXCEEDED);
        }
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).changeSortOrder(max + i + 1);
        flush(); // MySQL UNIQUE가 즉시 검사되므로 임시 순서를 먼저 반영
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).changeSortOrder(i + 1);
        flush();
        return ids.stream().map(id -> CourseContentResponse.from(byId.get(id))).toList();
    }

    private Course locked(Long id) {
        return courses.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
    }

    private CourseContent find(Long courseId, Long contentId) {
        return contents.findByIdAndCourseId(contentId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_CONTENT_NOT_FOUND));
    }

    private void flush() {
        try {
            contents.flush();
        } catch (DataIntegrityViolationException exception) {
            throw UniqueConstraintErrors.translate(exception);
        }
    }
}

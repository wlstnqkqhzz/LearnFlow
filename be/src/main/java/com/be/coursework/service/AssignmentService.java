package com.be.coursework.service;

import com.be.course.entity.Course;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.coursework.dto.*;
import com.be.coursework.entity.Assignment;
import com.be.coursework.repository.AssignmentRepository;
import com.be.global.exception.*;
import com.be.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @RequiredArgsConstructor @Transactional(readOnly = true)
public class AssignmentService {
    private final CourseRepository courses;
    private final AssignmentRepository assignments;
    private final CourseworkAccess access;
    private final AssignmentPolicy policy;

    @Transactional
    public AssignmentResponse create(@NotNull @Positive Long courseId, MemberPrincipal principal,
            @NotNull @Valid AssignmentCreateRequest request) {
        var course = course(courseId, principal, true);
        policy.requireDate(course, request.dueDate());
        if (assignments.existsByCourseIdAndSortOrder(courseId, request.sortOrder()))
            throw new BusinessException(ErrorCode.DUPLICATE_ASSIGNMENT_SORT_ORDER);
        return AssignmentResponse.from(assignments.saveAndFlush(Assignment.create(course, request.title(), request.description(),
            request.required(), request.dueDate(), request.passingScore(), request.sortOrder())));
    }
    public List<AssignmentResponse> list(@NotNull @Positive Long courseId, MemberPrincipal principal) {
        course(courseId, principal, false);
        return assignments.findByCourseIdOrderBySortOrderAsc(courseId).stream().map(AssignmentResponse::from).toList();
    }
    public AssignmentResponse get(@NotNull @Positive Long courseId, @NotNull @Positive Long assignmentId, MemberPrincipal principal) {
        course(courseId, principal, false);
        return AssignmentResponse.from(find(courseId, assignmentId));
    }
    @Transactional
    public AssignmentResponse update(@NotNull @Positive Long courseId, @NotNull @Positive Long assignmentId,
            MemberPrincipal principal, @NotNull @Valid AssignmentPatchRequest request) {
        var course = course(courseId, principal, true);
        var a = find(courseId, assignmentId);
        var due = request.getDueDate() == null ? a.getDueDate() : request.getDueDate();
        policy.requireDate(course, due);
        int order = request.getSortOrder() == null ? a.getSortOrder() : request.getSortOrder();
        if (assignments.existsByCourseIdAndSortOrderAndIdNot(courseId, order, assignmentId))
            throw new BusinessException(ErrorCode.DUPLICATE_ASSIGNMENT_SORT_ORDER);
        a.update(request.getTitle() == null ? a.getTitle() : request.getTitle(),
            request.isDescriptionPresent() ? request.getDescription() : a.getDescription(),
            request.getRequired() == null ? a.isRequired() : request.getRequired(), due,
            request.getPassingScore() == null ? a.getPassingScore() : request.getPassingScore(), order);
        assignments.flush();
        return AssignmentResponse.from(a);
    }
    @Transactional
    public void delete(@NotNull @Positive Long courseId, @NotNull @Positive Long assignmentId, MemberPrincipal principal) {
        course(courseId, principal, true);
        assignments.delete(find(courseId, assignmentId));
        assignments.flush();
    }
    @Transactional
    public List<AssignmentResponse> reorder(@NotNull @Positive Long courseId, MemberPrincipal principal,
            @NotNull @Valid AssignmentOrderRequest request) {
        course(courseId, principal, true);
        var current = assignments.findByCourseIdOrderBySortOrderAsc(courseId);
        var byId = current.stream().collect(Collectors.toMap(Assignment::getId, Function.identity()));
        var ids = request.assignmentIds();
        if (ids.size() != byId.size() || ids.size() != new HashSet<>(ids).size() || !byId.keySet().equals(new HashSet<>(ids)))
            throw new BusinessException(ErrorCode.INVALID_ASSIGNMENT_ORDER);
        int max = current.stream().mapToInt(Assignment::getSortOrder).max().orElse(0);
        if ((long) max + ids.size() > Integer.MAX_VALUE) throw new BusinessException(ErrorCode.INVALID_ASSIGNMENT_ORDER);
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).changeSortOrder(max + i + 1);
        assignments.flush();
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).changeSortOrder(i + 1);
        assignments.flush();
        return ids.stream().map(id -> AssignmentResponse.from(byId.get(id))).toList();
    }
    private Course course(Long id, MemberPrincipal principal, boolean write) {
        var course = (write ? courses.findByIdForUpdate(id) : courses.findById(id))
            .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        access.manage(course, principal);
        if (write && course.getStatus() != CourseStatus.DRAFT)
            throw new BusinessException(ErrorCode.ASSIGNMENT_CONFIGURATION_LOCKED);
        return course;
    }
    private Assignment find(Long courseId, Long id) {
        return assignments.findByIdAndCourseId(id, courseId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
    }
}

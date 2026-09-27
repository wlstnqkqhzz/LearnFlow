package com.be.coursework.service;
import com.be.course.entity.Course;
import com.be.coursework.repository.AssignmentRepository;
import com.be.global.exception.*;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class AssignmentPolicy {
    private final AssignmentRepository assignments;

    // DRAFT 날짜가 아직 없다면 존재하는 경계만 확인하고, 공개 시 전체 기간을 재검증한다.
    public void requireDate(Course course, LocalDate dueDate) {
        if (dueDate == null || (course.getStartDate() != null && dueDate.isBefore(course.getStartDate()))
                || (course.getEndDate() != null && dueDate.isAfter(course.getEndDate())))
            throw new BusinessException(ErrorCode.INVALID_ASSIGNMENT_DUE_DATE);
    }
    public void requirePeriod(Long courseId, LocalDate start, LocalDate end) {
        if (assignments.hasOutsidePeriod(courseId, start, end))
            throw new BusinessException(ErrorCode.INVALID_ASSIGNMENT_DUE_DATE);
    }
    public boolean blocksEnrollment(Course course, LocalDate today) {
        return assignments.existsByCourseIdAndIsRequiredTrueAndDueDateBefore(course.getId(), today)
            || (course.getEndDate() != null && course.getEndDate().isBefore(today)
                && assignments.existsByCourseIdAndIsRequiredTrue(course.getId()));
    }
}

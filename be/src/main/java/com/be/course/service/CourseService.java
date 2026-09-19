package com.be.course.service;

import com.be.assignment.service.AutoAssignmentService;
import com.be.course.dto.*;
import com.be.course.entity.Course;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.global.exception.*;
import com.be.member.entity.Member;
import com.be.member.enums.Role;
import com.be.member.repository.MemberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 특정 기간에 운영되는 교육과정 관리: 수강·수료·시험 처리는 포함하지 않음
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {
    private final CourseRepository courses;
    private final MemberRepository members;
    // 상태 변경 성공 후 같은 트랜잭션에서 배정 처리
    private final AutoAssignmentService autoAssignment;

    @Transactional
    public CourseResponse create(@NotNull @Valid CourseCreateRequest request) {
        validateDates(CourseStatus.DRAFT, request.startDate(), request.endDate());
        Member instructor = instructor(request.instructorId());
        Course course = Course.create(request.title(), request.description(), request.courseType(),
                request.startDate(), request.endDate(), request.passingProgressRate(), instructor);
        return CourseResponse.from(courses.saveAndFlush(course));
    }

    public CourseResponse get(@NotNull @Positive Long id) {
        return CourseResponse.from(courses.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND)));
    }

    // 제목 검색에서 와일드카드 문자는 리터럴로 취급
    public Page<CourseResponse> search(@NotNull @Valid CourseSearchRequest request) {
        String keyword = request.keyword();
        if (keyword != null) {
            keyword = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        }
        return courses.search(request.status(), request.type(), request.instructorId(), keyword,
                PageRequest.of(request.page(), request.size(), Sort.by("id"))).map(CourseResponse::from);
    }

    // 병합 후 전체 값을 검증하므로 OPEN/CLOSED 과정의 날짜 제거도 DB CHECK와 일치하게 거부
    @Transactional
    public CourseResponse update(@NotNull @Positive Long id, @NotNull @Valid CoursePatchRequest request) {
        Course course = locked(id);
        LocalDate start = request.isStartDatePresent() ? request.getStartDate() : course.getStartDate();
        LocalDate end = request.isEndDatePresent() ? request.getEndDate() : course.getEndDate();
        validateDates(course.getStatus(), start, end);
        Long instructorId = request.isInstructorIdPresent() ? request.getInstructorId()
                : course.getInstructor() == null ? null : course.getInstructor().getId();
        Member instructor = instructor(instructorId);
        course.update(request.getTitle() == null ? course.getTitle() : request.getTitle(),
                request.isDescriptionPresent() ? request.getDescription() : course.getDescription(),
                request.getCourseType() == null ? course.getCourseType() : request.getCourseType(),
                start, end, request.getPassingProgressRate() == null
                        ? course.getPassingProgressRate() : request.getPassingProgressRate(), instructor);
        courses.flush();
        return CourseResponse.from(course);
    }

    // 정방향 전이만 허용: 동일 상태 요청도 전이가 아니므로 충돌로 반환
    @Transactional
    public CourseResponse changeStatus(@NotNull @Positive Long id, @NotNull @Valid CourseStatusRequest request) {
        Course course = locked(id);
        CourseStatus next = request.status();
        if (!((course.getStatus() == CourseStatus.DRAFT && next == CourseStatus.OPEN)
                || (course.getStatus() == CourseStatus.OPEN && next == CourseStatus.CLOSED))) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_STATUS_TRANSITION);
        }
        validateDates(next, course.getStartDate(), course.getEndDate());
        if (next == CourseStatus.OPEN && course.getInstructor() != null) {
            instructor(course.getInstructor().getId());
        }
        course.changeStatus(next);
        courses.flush();
        // 상태 검증·변경 성공 후 같은 트랜잭션에서 활성 규칙 평가
        if (next == CourseStatus.OPEN) autoAssignment.assignCourse(id);
        return CourseResponse.from(course);
    }

    private Course locked(Long id) {
        return courses.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
    }

    // 강사 미지정은 허용하며, 상태별 강사 필수 조건을 추가하지 않음
    private Member instructor(Long id) {
        if (id == null) return null;
        Member member = members.findWithRolesById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!member.getRoles().contains(Role.INSTRUCTOR)) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_INSTRUCTOR);
        }
        return member;
    }

    private void validateDates(CourseStatus status, LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_PERIOD);
        }
        if (status != CourseStatus.DRAFT && (start == null || end == null)) {
            throw new BusinessException(ErrorCode.COURSE_DATES_REQUIRED);
        }
    }
}

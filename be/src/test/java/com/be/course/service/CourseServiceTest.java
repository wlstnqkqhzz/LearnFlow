package com.be.course.service;

import com.be.course.dto.*;
import com.be.course.entity.Course;
import com.be.course.enums.*;
import com.be.course.repository.CourseRepository;
import com.be.global.exception.*;
import com.be.member.entity.Member;
import com.be.member.enums.Role;
import com.be.member.repository.MemberRepository;
import com.be.organization.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 교육과정 생성·일반 수정·공개 조건·정방향 상태 전이 검증
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    @Mock CourseRepository courses;
    @Mock MemberRepository members;
    CourseService service;
    final LocalDate start = LocalDate.of(2026, 9, 1);
    final LocalDate end = start.plusDays(30);

    @BeforeEach
    void setUp() { service = new CourseService(courses, members); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createsDraftWithOptionalInstructor(boolean withInstructor) {
        if (withInstructor) instructor(true);
        when(courses.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(request(start, end, withInstructor ? 2L : null));
        assertThat(result.status()).isEqualTo(CourseStatus.DRAFT);
        assertThat(result.passingProgressRate()).isEqualByComparingTo("100");
        assertThat(result.instructorId()).isEqualTo(withInstructor ? 2L : null);
    }

    @Test
    void rejectsUnqualifiedOrMissingInstructor() {
        instructor(false);
        assertCode(ErrorCode.INVALID_COURSE_INSTRUCTOR, () -> service.create(request(start, end, 2L)));
        assertCode(ErrorCode.MEMBER_NOT_FOUND, () -> service.create(request(start, end, 99L)));
        verifyNoInteractions(courses);
    }

    @Test
    void getsCourseAndRejectsMissing() {
        Course course = course(start, end);
        when(courses.findById(1L)).thenReturn(Optional.of(course));
        assertThat(service.get(1L).id()).isEqualTo(1L);
        assertCode(ErrorCode.COURSE_NOT_FOUND, () -> service.get(99L));
    }

    @Test
    void rejectsReversedPeriodBeforeSaving() {
        assertCode(ErrorCode.INVALID_COURSE_PERIOD, () -> service.create(request(end, start, null)));
        verifyNoInteractions(courses);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void opensWithOrWithoutInstructor(boolean withInstructor) {
        Course course = course(start, end);
        if (withInstructor) {
            Member instructor = instructor(true);
            course.update(course.getTitle(), null, course.getCourseType(), start, end,
                    course.getPassingProgressRate(), instructor);
        }
        lock(course);
        assertThat(service.changeStatus(1L, new CourseStatusRequest(CourseStatus.OPEN)).status())
                .isEqualTo(CourseStatus.OPEN);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requiresBothDatesToOpen(boolean missingStart) {
        Course course = course(missingStart ? null : start, missingStart ? end : null);
        lock(course);
        assertCode(ErrorCode.COURSE_DATES_REQUIRED,
                () -> service.changeStatus(1L, new CourseStatusRequest(CourseStatus.OPEN)));
        assertThat(course.getStatus()).isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    void rechecksInstructorRoleWhenOpening() {
        Member instructor = instructor(false);
        Course course = Course.create("교육", null, CourseType.OPTIONAL, start, end, BigDecimal.TEN, instructor);
        lock(course);
        assertCode(ErrorCode.INVALID_COURSE_INSTRUCTOR,
                () -> service.changeStatus(1L, new CourseStatusRequest(CourseStatus.OPEN)));
    }

    @Test
    void closesOpenCourse() {
        Course course = course(start, end);
        course.changeStatus(CourseStatus.OPEN);
        lock(course);
        assertThat(service.changeStatus(1L, new CourseStatusRequest(CourseStatus.CLOSED)).status())
                .isEqualTo(CourseStatus.CLOSED);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT,CLOSED", "OPEN,DRAFT", "CLOSED,OPEN", "CLOSED,DRAFT", "DRAFT,DRAFT", "OPEN,OPEN", "CLOSED,CLOSED"})
    void rejectsInvalidTransitions(CourseStatus current, CourseStatus next) {
        Course course = course(start, end);
        course.changeStatus(current);
        lock(course);
        assertCode(ErrorCode.INVALID_COURSE_STATUS_TRANSITION,
                () -> service.changeStatus(1L, new CourseStatusRequest(next)));
        assertThat(course.getStatus()).isEqualTo(current);
    }

    @Test
    void updatesOnlySuppliedFieldsAndAllowsNullInstructorOnOpen() {
        Course course = course(start, end);
        course.changeStatus(CourseStatus.OPEN);
        lock(course);
        var patch = new CoursePatchRequest();
        patch.setTitle("수정 제목");
        patch.setInstructorId(null);
        var response = service.update(1L, patch);
        assertThat(response.title()).isEqualTo("수정 제목");
        assertThat(response.startDate()).isEqualTo(start);
        assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
        assertThat(response.instructorId()).isNull();
    }

    @Test
    void validatesMergedDatesAndDoesNotPartiallyMutate() {
        Course course = course(start, end);
        lock(course);
        var patch = new CoursePatchRequest();
        patch.setTitle("적용되면 안 되는 제목");
        patch.setStartDate(end.plusDays(1));
        assertCode(ErrorCode.INVALID_COURSE_PERIOD, () -> service.update(1L, patch));
        assertThat(course.getTitle()).isEqualTo("교육");
    }

    @ParameterizedTest
    @EnumSource(value = CourseStatus.class, names = {"OPEN", "CLOSED"})
    void publishedCourseCannotLoseDatesButCanEditTitle(CourseStatus status) {
        Course course = course(start, end);
        course.changeStatus(status);
        lock(course);
        var patch = new CoursePatchRequest();
        patch.setStartDate(null);
        assertCode(ErrorCode.COURSE_DATES_REQUIRED, () -> service.update(1L, patch));
        var title = new CoursePatchRequest();
        title.setTitle("새 제목");
        assertThat(service.update(1L, title).title()).isEqualTo("새 제목");
    }

    @Test
    void draftCanClearNullableFields() {
        Course course = course(start, end);
        lock(course);
        var patch = new CoursePatchRequest();
        patch.setStartDate(null);
        patch.setEndDate(null);
        patch.setDescription(null);
        var result = service.update(1L, patch);
        assertThat(result.startDate()).isNull();
        assertThat(result.endDate()).isNull();
        assertThat(result.description()).isNull();
    }

    @Test
    void changingInstructorValidatesRole() {
        lock(course(start, end));
        instructor(false);
        var patch = new CoursePatchRequest();
        patch.setInstructorId(2L);
        assertCode(ErrorCode.INVALID_COURSE_INSTRUCTOR, () -> service.update(1L, patch));
    }

    @Test
    void searchesWithPaginationAndEscapedKeyword() {
        Pageable page = PageRequest.of(0, 20, Sort.by("id"));
        when(courses.search(null, null, null, "%a!%!_!!%", page)).thenReturn(Page.empty(page));
        assertThat(service.search(new CourseSearchRequest(null, null, null, null, null, " a%_! "))
                .getSize()).isEqualTo(20);
    }

    private CourseCreateRequest request(LocalDate start, LocalDate end, Long instructor) {
        return new CourseCreateRequest(" 교육 ", null, CourseType.MANDATORY, start, end, null, instructor);
    }

    @Test
    void opensSingleDayCourseAndUnassignsExistingInstructor() {
        var teacher = mock(Member.class);
        Course course = Course.create("교육", null, CourseType.OPTIONAL, start, start, BigDecimal.TEN, teacher);
        lock(course);
        var patch = new CoursePatchRequest();
        patch.setInstructorId(null);
        assertThat(service.update(1L, patch).instructorId()).isNull();
        assertThat(service.changeStatus(1L, new CourseStatusRequest(CourseStatus.OPEN)).status())
                .isEqualTo(CourseStatus.OPEN);
        verifyNoInteractions(members);
    }

    private Course course(LocalDate start, LocalDate end) {
        Course course = Course.create("교육", "설명", CourseType.MANDATORY, start, end, BigDecimal.TEN, null);
        ReflectionTestUtils.setField(course, "id", 1L);
        return course;
    }

    private void lock(Course course) { when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course)); }

    private Member instructor(boolean qualified) {
        Member member = Member.create("E002", "teacher@example.com", "hash", "강사",
                Department.create("D", "부서", null), JobPosition.create("J", "직무"), start);
        ReflectionTestUtils.setField(member, "id", 2L);
        if (qualified) member.addRole(Role.INSTRUCTOR);
        when(members.findWithRolesById(2L)).thenReturn(Optional.of(member));
        return member;
    }

    private void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}

package com.be.coursework;
import com.be.course.entity.Course;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.coursework.dto.*;
import com.be.coursework.entity.Assignment;
import com.be.coursework.repository.*;
import com.be.coursework.service.*;
import com.be.global.exception.*;
import com.be.member.enums.Role;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.be.coursework.CourseworkFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentServiceTest {
    final CourseRepository courses = mock(CourseRepository.class);
    final AssignmentRepository assignments = mock(AssignmentRepository.class);
    final AssignmentService service = new AssignmentService(courses, assignments, new CourseworkAccess(), new AssignmentPolicy(assignments));
    Course course;
    @BeforeEach void setUp() {
        course = course(CourseStatus.DRAFT);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(courses.findById(1L)).thenReturn(Optional.of(course));
        when(assignments.saveAndFlush(any())).thenAnswer(c -> c.getArgument(0));
    }
    @Test void draftCrudForAdminAndAssignedInstructor() {
        var result = service.create(1L, ADMIN, request());
        assertThat(result.title()).isEqualTo("과제");
        var a = assignment(course, 20, true);
        when(assignments.findByIdAndCourseId(20L, 1L)).thenReturn(Optional.of(a));
        when(assignments.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(a));
        assertThat(service.list(1L, INSTRUCTOR)).hasSize(1);
        assertThat(service.get(1L, 20L, INSTRUCTOR).assignmentId()).isEqualTo(20);
        var patch = new AssignmentPatchRequest(); patch.setTitle("수정"); patch.setDescription(null); patch.setRequired(false);
        assertThat(service.update(1L, 20L, INSTRUCTOR, patch).required()).isFalse();
        assertThat(a.getTitle()).isEqualTo("수정");
        service.delete(1L, 20L, ADMIN);
        verify(assignments).delete(a);
    }
    @ParameterizedTest @EnumSource(value = CourseStatus.class, names = {"OPEN", "CLOSED"})
    void allConfigurationWritesBlockedAfterDraft(CourseStatus status) {
        course.changeStatus(status);
        error(() -> service.create(1L, ADMIN, request()), ErrorCode.ASSIGNMENT_CONFIGURATION_LOCKED);
        error(() -> service.update(1L, 20L, INSTRUCTOR, new AssignmentPatchRequest()), ErrorCode.ASSIGNMENT_CONFIGURATION_LOCKED);
        error(() -> service.delete(1L, 20L, ADMIN), ErrorCode.ASSIGNMENT_CONFIGURATION_LOCKED);
        error(() -> service.reorder(1L, ADMIN, new AssignmentOrderRequest(List.of())), ErrorCode.ASSIGNMENT_CONFIGURATION_LOCKED);
        verifyNoInteractions(assignments);
    }
    @Test void ownershipCheckedForReadAndWrite() {
        for (var p : List.of(OWNER, principal(99, Role.INSTRUCTOR))) {
            error(() -> service.list(1L, p), ErrorCode.ASSIGNMENT_ACCESS_DENIED);
            error(() -> service.create(1L, p, request()), ErrorCode.ASSIGNMENT_ACCESS_DENIED);
        }
        course.update(course.getTitle(), null, course.getCourseType(), course.getStartDate(), course.getEndDate(), BigDecimal.TEN, null);
        error(() -> service.list(1L, INSTRUCTOR), ErrorCode.ASSIGNMENT_ACCESS_DENIED);
        assertThat(service.list(1L, ADMIN)).isEmpty();
    }
    @Test void datePeriodAndDuplicateOrderValidatedBeforeSave() {
        error(() -> service.create(1L, ADMIN, new AssignmentCreateRequest("과제", null, true, DUE.plusDays(1), BigDecimal.TEN, 1)),
            ErrorCode.INVALID_ASSIGNMENT_DUE_DATE);
        error(() -> service.create(1L, ADMIN, new AssignmentCreateRequest("과제", null, true, DUE.minusDays(11), BigDecimal.TEN, 1)),
            ErrorCode.INVALID_ASSIGNMENT_DUE_DATE);
        when(assignments.existsByCourseIdAndSortOrder(1L, 1)).thenReturn(true);
        error(() -> service.create(1L, ADMIN, request()), ErrorCode.DUPLICATE_ASSIGNMENT_SORT_ORDER);
        verify(assignments, never()).saveAndFlush(any());
    }
    @Test void reorderRequiresWholeUniqueSetAndUsesTemporaryPositiveRange() {
        Assignment a = assignment(course, 20, true), b = assignment(course, 21, false);
        when(assignments.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(a, b));
        error(() -> service.reorder(1L, ADMIN, new AssignmentOrderRequest(List.of(20L,20L))), ErrorCode.INVALID_ASSIGNMENT_ORDER);
        error(() -> service.reorder(1L, ADMIN, new AssignmentOrderRequest(List.of(20L))), ErrorCode.INVALID_ASSIGNMENT_ORDER);
        var result = service.reorder(1L, INSTRUCTOR, new AssignmentOrderRequest(List.of(21L,20L)));
        assertThat(result).extracting(AssignmentResponse::assignmentId).containsExactly(21L,20L);
        assertThat(a.getSortOrder()).isEqualTo(2); assertThat(b.getSortOrder()).isEqualTo(1);
        verify(assignments, times(2)).flush();
    }
    @Test void assignmentLookupAlwaysUsesCourseId() {
        error(() -> service.get(1L, 999L, ADMIN), ErrorCode.ASSIGNMENT_NOT_FOUND);
        verify(assignments).findByIdAndCourseId(999L,1L);
    }
    private AssignmentCreateRequest request() { return new AssignmentCreateRequest(" 과제 ", null, true, DUE, BigDecimal.TEN, 1); }
    static void error(Runnable task, ErrorCode code) {
        assertThatThrownBy(task::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}

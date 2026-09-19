package com.be.course.service;

import com.be.course.dto.*;
import com.be.course.entity.*;
import com.be.course.enums.*;
import com.be.course.repository.*;
import com.be.global.exception.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 콘텐츠 소속·중복 순서·전체 재정렬·FK 삭제 실패 계약 검증
@ExtendWith(MockitoExtension.class)
class CourseContentServiceTest {
    @Mock CourseRepository courses;
    @Mock CourseContentRepository contents;
    CourseContentService service;
    Course course;

    @BeforeEach
    void setUp() {
        service = new CourseContentService(courses, contents);
        course = Course.create("교육", null, CourseType.MANDATORY, null, null, BigDecimal.TEN, null);
        ReflectionTestUtils.setField(course, "id", 1L);
    }

    @Test
    void createsContentWithoutChangingEntityDefaults() {
        lock();
        var result = service.create(1L, request());
        assertThat(result.courseId()).isEqualTo(1L);
        assertThat(result.sortOrder()).isEqualTo(1);
        assertThat(result.required()).isTrue();
        verify(contents).saveAndFlush(any());
    }

    @Test
    void rejectsMissingCourseOnCreateAndList() {
        assertCode(ErrorCode.COURSE_NOT_FOUND, () -> service.create(99L, request()));
        assertCode(ErrorCode.COURSE_NOT_FOUND, () -> service.getAll(99L));
        verifyNoInteractions(contents);
    }

    @Test
    void rejectsOccupiedOrder() {
        lock();
        when(contents.existsByCourseIdAndSortOrder(1L, 1)).thenReturn(true);
        assertCode(ErrorCode.DUPLICATE_CONTENT_SORT_ORDER, () -> service.create(1L, request()));
        verify(contents, never()).saveAndFlush(any());
    }

    @Test
    void readsOneAndListsInRepositoryOrder() {
        var first = content(10L, 1);
        var second = content(20L, 5);
        when(contents.findByIdAndCourseId(10L, 1L)).thenReturn(Optional.of(first));
        when(courses.existsById(1L)).thenReturn(true);
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(first, second));
        assertThat(service.get(1L, 10L).id()).isEqualTo(10L);
        assertThat(service.getAll(1L)).extracting(CourseContentResponse::sortOrder).containsExactly(1, 5);
    }

    @Test
    void updatesSelectedFieldsAndClearsDuration() {
        lock();
        var content = content(10L, 1);
        when(contents.findByIdAndCourseId(10L, 1L)).thenReturn(Optional.of(content));
        var patch = new CourseContentPatchRequest();
        patch.setTitle("변경");
        patch.setRequired(false);
        patch.setDurationSeconds(null);
        patch.setSortOrder(4);
        var result = service.update(1L, 10L, patch);
        assertThat(result.title()).isEqualTo("변경");
        assertThat(result.required()).isFalse();
        assertThat(result.durationSeconds()).isNull();
        assertThat(result.sortOrder()).isEqualTo(4);
        assertThat(result.contentType()).isEqualTo(ContentType.VIDEO);
    }

    @Test
    void cannotUpdateToOccupiedOrder() {
        lock();
        when(contents.findByIdAndCourseId(10L, 1L)).thenReturn(Optional.of(content(10L, 1)));
        when(contents.existsByCourseIdAndSortOrderAndIdNot(1L, 2, 10L)).thenReturn(true);
        var patch = new CourseContentPatchRequest();
        patch.setSortOrder(2);
        assertCode(ErrorCode.DUPLICATE_CONTENT_SORT_ORDER, () -> service.update(1L, 10L, patch));
    }

    @Test
    void deletesOnlyContentAndFlushes() {
        lock();
        var content = content(10L, 1);
        when(contents.findByIdAndCourseId(10L, 1L)).thenReturn(Optional.of(content));
        service.delete(1L, 10L);
        verify(contents).delete(content);
        verify(contents).flush();
        verify(courses, never()).delete(any());
    }

    @Test
    void preservesFkRestrictErrorOnDelete() {
        lock();
        when(contents.findByIdAndCourseId(10L, 1L)).thenReturn(Optional.of(content(10L, 1)));
        var failure = new DataIntegrityViolationException("FK restrict");
        doThrow(failure).when(contents).flush();
        assertThatThrownBy(() -> service.delete(1L, 10L)).isSameAs(failure);
    }

    @ParameterizedTest
    @ValueSource(strings = {"get", "update", "delete"})
    void wrongCourseOrMissingContentNeverSucceeds(String operation) {
        if (!operation.equals("get")) lock();
        assertCode(ErrorCode.COURSE_CONTENT_NOT_FOUND, () -> {
            switch (operation) {
                case "get" -> service.get(1L, 99L);
                case "update" -> service.update(1L, 99L, new CourseContentPatchRequest());
                case "delete" -> service.delete(1L, 99L);
            }
        });
        verify(contents).findByIdAndCourseId(99L, 1L);
        verify(contents, never()).delete(any());
    }

    @Test
    void reorderFlushesUniquePositiveTemporaryValuesBeforeFinalOrder() {
        lock();
        var first = content(10L, 1);
        var second = content(20L, 2);
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(first, second));
        List<List<Integer>> flushSnapshots = new ArrayList<>();
        doAnswer(call -> { flushSnapshots.add(List.of(first.getSortOrder(), second.getSortOrder())); return null; })
                .when(contents).flush();
        var result = service.reorder(1L, new ContentOrderRequest(List.of(20L, 10L)));
        assertThat(flushSnapshots).containsExactly(List.of(4, 3), List.of(2, 1));
        assertThat(result).extracting(CourseContentResponse::id).containsExactly(20L, 10L);
        assertThat(result).extracting(CourseContentResponse::sortOrder).containsExactly(1, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "missing-or-foreign", "partial", "empty"})
    void rejectsInvalidFullOrderBeforeMutation(String kind) {
        lock();
        var first = content(10L, 1);
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(first, content(20L, 2)));
        List<Long> ids = switch (kind) {
            case "duplicate" -> List.of(10L, 10L);
            case "missing-or-foreign" -> List.of(10L, 99L);
            case "partial" -> List.of(10L);
            default -> List.of();
        };
        assertCode(ErrorCode.INVALID_CONTENT_ORDER, () -> service.reorder(1L, new ContentOrderRequest(ids)));
        assertThat(first.getSortOrder()).isEqualTo(1);
        verify(contents, never()).flush();
    }

    @Test
    void preventsIntegerOverflowInTemporaryOrder() {
        lock();
        when(contents.findByCourseIdOrderBySortOrderAsc(1L)).thenReturn(List.of(content(10L, Integer.MAX_VALUE)));
        assertCode(ErrorCode.CONTENT_ORDER_LIMIT_EXCEEDED,
                () -> service.reorder(1L, new ContentOrderRequest(List.of(10L))));
        verify(contents, never()).flush();
    }

    @Test
    void emptyCourseAcceptsEmptyOrder() {
        lock();
        assertThat(service.reorder(1L, new ContentOrderRequest(List.of()))).isEmpty();
    }

    private void lock() { when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course)); }

    private CourseContentCreateRequest request() {
        return new CourseContentCreateRequest("콘텐츠", ContentType.VIDEO, "https://example.com/video", 60, 1, null);
    }

    private CourseContent content(Long id, int order) {
        var content = CourseContent.create(course, "콘텐츠", ContentType.VIDEO, "https://example.com/video", 60, order, true);
        ReflectionTestUtils.setField(content, "id", id);
        return content;
    }

    private void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}

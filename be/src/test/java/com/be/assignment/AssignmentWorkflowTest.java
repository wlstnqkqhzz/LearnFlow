package com.be.assignment;

import com.be.assignment.dto.*;
import com.be.assignment.enums.AssignmentRuleType;
import com.be.assignment.repository.AssignmentRuleRepository;
import com.be.assignment.service.*;
import com.be.course.dto.CourseStatusRequest;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.course.service.CourseService;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.AssignmentSource;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.member.repository.MemberRepository;
import com.be.organization.repository.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.be.assignment.AssignmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 세 Service를 연결하여 초안 규칙 → OPEN → 비활성/재활성화 흐름 검증 (DB는 대체)
class AssignmentWorkflowTest {
    @Test
    void draftRuleWaitsForOpenAndReactivationOnlyAddsMissingMembers() {
        var courses = mock(CourseRepository.class);
        var rules = mock(AssignmentRuleRepository.class);
        var members = mock(MemberRepository.class);
        var enrollments = mock(EnrollmentRepository.class);
        var auto = new AutoAssignmentService(courses, rules, members, enrollments, CLOCK, mock(EntityManager.class), mock(com.be.notification.service.NotificationService.class));
        var ruleService = new AssignmentRuleService(rules, courses, mock(DepartmentRepository.class),
                mock(JobPositionRepository.class), auto);
        var courseService = new CourseService(courses, members, auto);
        var course = course(CourseStatus.DRAFT);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(rules.saveAndFlush(any())).thenAnswer(call -> { id(call.getArgument(0), 100L); return call.getArgument(0); });
        ruleService.create(1L, new AssignmentRuleCreateRequest(AssignmentRuleType.ALL_EMPLOYEES, null, null, null, true));
        verifyNoInteractions(enrollments);
        var capture = ArgumentCaptor.forClass(com.be.assignment.entity.AssignmentRule.class);
        verify(rules).saveAndFlush(capture.capture());
        var rule = capture.getValue();
        when(rules.findForUpdate(1L, 100L)).thenReturn(Optional.of(rule));
        when(rules.findActiveForAssignment(1L)).thenAnswer(call -> rule.isActive() ? List.of(rule) : List.of());
        var firstMember = member(2L);
        var laterMember = member(3L);
        List<com.be.member.entity.Member> targets = new ArrayList<>(List.of(firstMember));
        when(members.findAssignmentCandidates(null, null, null, null)).thenAnswer(call -> List.copyOf(targets));
        Map<Long, Enrollment> stored = new HashMap<>();
        when(enrollments.findExistingForAssignment(anyLong(), eq(1L)))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(enrollments.saveAndFlush(any())).thenAnswer(call -> {
            Enrollment value = call.getArgument(0);
            stored.put(value.getMember().getId(), value);
            return value;
        });
        courseService.changeStatus(1L, new CourseStatusRequest(CourseStatus.OPEN));
        assertThat(stored).containsOnlyKeys(2L);
        Enrollment original = stored.get(2L);
        ruleService.changeStatus(1L, 100L, new AssignmentRuleStatusRequest(false));
        targets.add(laterMember);
        auto.assignCourse(1L);
        assertThat(stored).containsOnlyKeys(2L);
        ruleService.changeStatus(1L, 100L, new AssignmentRuleStatusRequest(true));
        ruleService.changeStatus(1L, 100L, new AssignmentRuleStatusRequest(true));
        assertThat(stored).containsOnlyKeys(2L, 3L);
        assertThat(stored.get(2L)).isSameAs(original);
        assertThat(original.getAssignmentSource()).isEqualTo(AssignmentSource.AUTOMATIC);
        verify(enrollments, times(2)).saveAndFlush(any());
        verify(enrollments, never()).delete(any());
    }
}

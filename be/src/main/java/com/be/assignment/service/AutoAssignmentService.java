package com.be.assignment.service;

import com.be.assignment.entity.AssignmentRule;
import com.be.assignment.repository.AssignmentRuleRepository;
import com.be.course.entity.Course;
import com.be.course.enums.CourseStatus;
import com.be.course.repository.CourseRepository;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.repository.EnrollmentRepository;
import com.be.global.exception.*;
import com.be.member.entity.Member;
import com.be.member.enums.MemberStatus;
import com.be.member.repository.MemberRepository;
import jakarta.persistence.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 자동 배정 판단을 한 곳에 집중하며 호출자의 트랜잭션에 참여
@Service
@RequiredArgsConstructor
@Transactional
public class AutoAssignmentService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final CourseRepository courses;
    private final AssignmentRuleRepository rules;
    private final MemberRepository members;
    private final EnrollmentRepository enrollments;
    private final Clock clock;
    private final EntityManager entityManager;
    private final com.be.notification.service.NotificationService notifications;

    // Course OPEN 직후 활성 규칙을 ID 순으로 평가
    public void assignCourse(Long courseId) {
        Course course = locked(courseId);
        if (course.getStatus() != CourseStatus.OPEN) return;
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        for (var rule : rules.findActiveForAssignment(courseId)) assignTargets(course, rule, today);
    }

    // 규칙 생성·조건 수정·활성화는 해당 규칙의 현재 대상만 평가
    public void assignRule(Long courseId, Long ruleId) {
        Course course = locked(courseId);
        if (course.getStatus() != CourseStatus.OPEN) return;
        AssignmentRule rule = rules.findForUpdate(courseId, ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_RULE_NOT_FOUND));
        if (rule.isActive()) assignTargets(course, rule, LocalDate.now(clock.withZone(SEOUL)));
    }

    // 회원 생성/변경 호출자는 해당 회원을 이미 저장·잠금한 상태여야 함
    public void assignMember(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE) return;
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        for (Course course : courses.findOpenForAssignment()) {
            for (AssignmentRule rule : rules.findActiveForAssignment(course.getId())) {
                if (matches(rule, member, today)) {
                    assignIfMissing(member, course, rule);
                    break; // 동일 과정에서는 가장 먼저 일치한 규칙만 기록
                }
            }
        }
    }

    private Course locked(Long id) {
        return courses.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
    }

    // DB에서 ACTIVE 및 직접 소속/입사일 범위로 후보를 줄임
    private void assignTargets(Course course, AssignmentRule rule, LocalDate today) {
        Long departmentId = rule.getDepartment() == null ? null : rule.getDepartment().getId();
        Long positionId = rule.getJobPosition() == null ? null : rule.getJobPosition().getId();
        LocalDate from = rule.getNewEmployeeDays() == null ? null : today.minusDays(rule.getNewEmployeeDays() - 1L);
        LocalDate to = from == null ? null : today;
        for (Member member : members.findAssignmentCandidates(departmentId, positionId, from, to)) {
            // 기존 영속성 컨텍스트에 강사로 먼저 로드된 회원도 최신 잠금 상태로 평가
            entityManager.refresh(member, LockModeType.PESSIMISTIC_WRITE);
            if (matches(rule, member, today)) assignIfMissing(member, course, rule);
        }
    }

    // 조건에서 벗어난 경우 아무것도 하지 않으며 기존 배정의 출처·규칙·상태는 불변
    private boolean matches(AssignmentRule rule, Member member, LocalDate today) {
        if (!rule.isActive() || member.getStatus() != MemberStatus.ACTIVE) return false;
        return switch (rule.getRuleType()) {
            case ALL_EMPLOYEES -> true;
            case DEPARTMENT -> rule.getDepartment().getId().equals(member.getDepartment().getId());
            case JOB_POSITION -> rule.getJobPosition().getId().equals(member.getJobPosition().getId());
            case NEW_EMPLOYEE -> {
                long days = ChronoUnit.DAYS.between(member.getHireDate(), today);
                yield days >= 0 && days < rule.getNewEmployeeDays();
            }
        };
    }

    private void assignIfMissing(Member member, Course course, AssignmentRule rule) {
        if (enrollments.findExistingForAssignment(member.getId(), course.getId()).isPresent()) return;
        try {
            var enrollment = enrollments.saveAndFlush(Enrollment.automatic(member, rule,
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
            notifications.notify(enrollment, com.be.notification.enums.NotificationType.ENROLLMENT_ASSIGNED);
        } catch (DataIntegrityViolationException exception) {
            // UNIQUE도 무시하지 않고 전체 트랜잭션 실패로 전파
            throw UniqueConstraintErrors.translate(exception);
        }
    }
}

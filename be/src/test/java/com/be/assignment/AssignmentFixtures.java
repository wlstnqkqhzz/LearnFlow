package com.be.assignment;

import com.be.assignment.entity.AssignmentRule;
import com.be.assignment.enums.AssignmentRuleType;
import com.be.course.entity.Course;
import com.be.course.enums.*;
import com.be.member.entity.Member;
import com.be.organization.entity.*;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.test.util.ReflectionTestUtils;

// 배정 테스트에서 공통으로 사용하는 실제 엔티티 및 고정 서울 날짜
public final class AssignmentFixtures {
    private AssignmentFixtures() {}
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T15:30:00Z"), ZoneOffset.UTC);
    public static final LocalDate TODAY = LocalDate.of(2026, 9, 19);
    public static Course course(CourseStatus status) {
        var value = Course.create("교육", null, CourseType.MANDATORY, TODAY, TODAY.plusDays(30), BigDecimal.TEN, null);
        id(value, 1L);
        value.changeStatus(status);
        return value;
    }
    public static Department department(long id) {
        var value = Department.create("D" + id, "부서", null);
        id(value, id);
        return value;
    }
    public static JobPosition position(long id) {
        var value = JobPosition.create("J" + id, "직무");
        id(value, id);
        return value;
    }
    public static Member member(long id) {
        var value = Member.create("E" + id, "member" + id + "@example.com", "hash", "직원" + id,
                department(10L), position(20L), TODAY);
        id(value, id);
        return value;
    }
    public static AssignmentRule rule(Course course, AssignmentRuleType type) {
        var value = AssignmentRule.create(course, type,
                type == AssignmentRuleType.DEPARTMENT ? department(10L) : null,
                type == AssignmentRuleType.JOB_POSITION ? position(20L) : null,
                type == AssignmentRuleType.NEW_EMPLOYEE ? (short) 90 : null, true);
        id(value, 100L);
        return value;
    }
    public static void id(Object entity, Long id) { ReflectionTestUtils.setField(entity, "id", id); }
}

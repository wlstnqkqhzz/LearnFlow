package com.be.enrollment;

import com.be.assignment.AssignmentFixtures;
import com.be.course.entity.*;
import com.be.course.enums.*;
import com.be.enrollment.entity.*;
import com.be.global.security.MemberPrincipal;
import com.be.member.enums.Role;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

// 실제 도메인 객체와 UTC 고정 시각을 사용하는 진도 테스트 데이터
final class ProgressFixtures {
    static final LocalDateTime NOW = LocalDateTime.now(AssignmentFixtures.CLOCK);
    static final MemberPrincipal OWNER = new MemberPrincipal(2L, "owner@example.com", Set.of(Role.EMPLOYEE));
    static Course course() {
        Course course = AssignmentFixtures.course(CourseStatus.OPEN);
        course.update("교육", null, CourseType.MANDATORY, course.getStartDate(), course.getEndDate(),
                new BigDecimal("80.00"), null);
        return course;
    }
    static Enrollment enrollment(Course course) {
        var enrollment = Enrollment.manual(AssignmentFixtures.member(2L), course, NOW.minusDays(1));
        AssignmentFixtures.id(enrollment, 10L);
        return enrollment;
    }
    static CourseContent content(Course course, long id, boolean required) {
        var content = CourseContent.create(course, "콘텐츠" + id, ContentType.VIDEO,
                "https://example.com/video", null, (int) id, required);
        AssignmentFixtures.id(content, id);
        return content;
    }
    static ContentProgress progress(Enrollment enrollment, CourseContent content, String rate) {
        var progress = ContentProgress.create(enrollment, content);
        progress.updateProgress(new BigDecimal(rate), NOW.minusHours(1));
        AssignmentFixtures.id(progress, content.getId());
        return progress;
    }
}

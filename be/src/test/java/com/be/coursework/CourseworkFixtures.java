package com.be.coursework;
import com.be.course.entity.Course;
import com.be.course.enums.*;
import com.be.coursework.entity.Assignment;
import com.be.enrollment.entity.Enrollment;
import com.be.global.security.MemberPrincipal;
import com.be.member.enums.Role;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;
import static com.be.assignment.AssignmentFixtures.*;
final class CourseworkFixtures {
    static final LocalDate DUE = LocalDate.of(2026, 9, 27);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T14:59:59Z"), ZoneId.of("America/Los_Angeles"));
    static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
    static final MemberPrincipal OWNER = principal(2, Role.EMPLOYEE);
    static final MemberPrincipal ADMIN = principal(3, Role.ADMIN);
    static final MemberPrincipal INSTRUCTOR = principal(4, Role.INSTRUCTOR);
    static MemberPrincipal principal(long id, Role role) { return new MemberPrincipal(id, "test@example.com", Set.of(role)); }
    static Course course(CourseStatus status) {
        var c = Course.create("과정", null, CourseType.MANDATORY, DUE.minusDays(10), DUE, new BigDecimal("100"), member(4));
        id(c, 1L); c.changeStatus(status); return c;
    }
    static Assignment assignment(Course c, long id, boolean required) {
        var a = Assignment.create(c, "과제", null, required, DUE, new BigDecimal("80"), (int) id);
        id(a, id); return a;
    }
    static Enrollment enrollment(Course c) {
        var e = Enrollment.manual(member(2), c, NOW.minusDays(2)); id(e, 10L); return e;
    }
}

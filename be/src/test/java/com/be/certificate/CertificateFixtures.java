package com.be.certificate;

import com.be.enrollment.entity.Enrollment;
import com.be.course.enums.CourseStatus;
import com.be.global.security.MemberPrincipal;
import com.be.member.enums.Role;
import java.time.*;
import java.util.Set;
import static com.be.assignment.AssignmentFixtures.*;

public final class CertificateFixtures {
    private CertificateFixtures() {}
    public static final LocalDateTime COMPLETED = LocalDateTime.of(2026, 9, 20, 16, 30);
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T16:30:00.123456789Z"), ZoneOffset.UTC);
    public static final MemberPrincipal OWNER = principal(2, Role.EMPLOYEE);
    public static final MemberPrincipal ADMIN = principal(99, Role.ADMIN);

    public static MemberPrincipal principal(long id, Role role) {
        return new MemberPrincipal(id, "member" + id + "@example.com", Set.of(role));
    }
    public static Enrollment completed() {
        var enrollment = Enrollment.manual(member(2), course(CourseStatus.OPEN), COMPLETED.minusDays(1));
        id(enrollment, 3L);
        enrollment.startLearning(COMPLETED.minusHours(1));
        enrollment.completeLearning(COMPLETED);
        return enrollment;
    }
}

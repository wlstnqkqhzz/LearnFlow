package com.be.coursework.service;
import com.be.course.entity.Course;
import com.be.enrollment.entity.Enrollment;
import com.be.global.exception.*;
import com.be.global.security.MemberPrincipal;
import com.be.member.enums.Role;
import java.util.Objects;
import org.springframework.stereotype.Component;
@Component
public class CourseworkAccess {
    public void manage(Course course, MemberPrincipal principal) {
        if (principal != null && principal.roles().contains(Role.ADMIN)) return;
        if (principal == null || !principal.roles().contains(Role.INSTRUCTOR) || course.getInstructor() == null
                || !Objects.equals(course.getInstructor().getId(), principal.memberId()))
            throw new BusinessException(ErrorCode.ASSIGNMENT_ACCESS_DENIED);
    }
    public void own(Enrollment enrollment, MemberPrincipal principal) {
        if (principal == null || !principal.roles().contains(Role.EMPLOYEE)
                || !Objects.equals(enrollment.getMember().getId(), principal.memberId()))
            throw new BusinessException(ErrorCode.ASSIGNMENT_ACCESS_DENIED);
    }
}

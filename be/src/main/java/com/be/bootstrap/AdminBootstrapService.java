package com.be.bootstrap;

import com.be.member.dto.MemberCreateRequest;
import com.be.member.entity.Member;
import com.be.member.enums.Role;
import com.be.member.repository.MemberRepository;
import com.be.organization.entity.Department;
import com.be.organization.entity.JobPosition;
import com.be.organization.repository.DepartmentRepository;
import com.be.organization.repository.JobPositionRepository;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminBootstrapService {
    // Existing UNIQUE constraints make these installation-wide bootstrap identities.
    static final String RESERVED = "LEARNFLOW_BOOTSTRAP_ADMIN";
    private final MemberRepository members;
    private final DepartmentRepository departments;
    private final JobPositionRepository positions;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final Clock clock;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean adminExists() { return members.existsWithRole(Role.ADMIN); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean create(AdminBootstrapProperties properties) {
        if (!properties.enabled() || members.existsWithRole(Role.ADMIN)) return false;
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        // Reuse the normal member input rules without logging rejected values.
        var input = new MemberCreateRequest(RESERVED, properties.email(), properties.password(),
                properties.name(), 1L, 1L, today);
        if (!validator.validate(input).isEmpty())
            throw new Failure("Initial admin bootstrap requires valid email, password (8-128 characters), and name");
        if (members.existsByEmail(input.email()))
            throw new Failure("Initial admin bootstrap email conflicts with an existing member; no account was changed");
        if (members.existsByEmployeeNumber(RESERVED) || departments.existsByCode(RESERVED) || positions.existsByCode(RESERVED))
            throw new Failure("Initial admin bootstrap reserved identity conflicts with existing data");

        // Insert this fixed UNIQUE code first. Concurrent bootstraps contend here even with different emails.
        // All three inserts commit together; a losing transaction is rolled back before a fresh ADMIN query.
        var department = departments.saveAndFlush(Department.create(RESERVED, "시스템 관리", null));
        var position = positions.saveAndFlush(JobPosition.create(RESERVED, "시스템 관리자"));
        var admin = Member.createInitialAdmin(RESERVED, input.email(), encoder.encode(input.password()),
                input.name(), department, position, today);
        members.saveAndFlush(admin);
        return true;
    }

    static final class Failure extends IllegalStateException {
        Failure(String message) { super(message); }
    }
}

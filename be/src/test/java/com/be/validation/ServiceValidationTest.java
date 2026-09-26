package com.be.validation;

import com.be.member.dto.*;
import com.be.member.service.MemberService;
import com.be.member.repository.MemberRepository;
import com.be.organization.dto.*;
import com.be.organization.repository.*;
import com.be.organization.service.*;
import jakarta.validation.*;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// Controller 없이도 Service 경계에서 Jakarta Validation이 적용되는지 검증
class ServiceValidationTest {
    private ValidatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
    }

    @AfterEach
    void close() {
        factory.close();
    }

    @Test
    void rejectsInvalidDepartmentInputBeforeRepositoryAccess() {
        DepartmentRepository repository = mock(DepartmentRepository.class);
        DepartmentService service = validated(new DepartmentService(repository));
        assertThatThrownBy(() -> service.create(new DepartmentCreateRequest(" ", "개발", null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.update(0L, new DepartmentUpdateRequest("개발", null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsInvalidJobPositionInput() {
        JobPositionRepository repository = mock(JobPositionRepository.class);
        JobPositionService service = validated(new JobPositionService(repository));
        assertThatThrownBy(() -> service.create(new JobPositionCreateRequest("A".repeat(51), "직무")))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsInvalidMemberInputBeforeRepositoryAccess() {
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = validated(new MemberService(repository,
                mock(DepartmentRepository.class), mock(JobPositionRepository.class),
                mock(PasswordEncoder.class), Clock.systemUTC(), mock(com.be.assignment.service.AutoAssignmentService.class)));
        assertThatThrownBy(() -> service.create(new MemberCreateRequest(
                "E001", "invalid-email", "short", "직원", null, -1L, null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.changeStatus(1L, new MemberStatusUpdateRequest(null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.addRole(1L, new MemberRoleUpdateRequest(null)))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsTrimmedEmailAndRejectsOversizedOrBlankFields() {
        var request = new MemberCreateRequest(" E001 ", " USER@Example.COM ", "password123!",
                " 직원 ", 1L, 2L, LocalDate.of(2026, 9, 18));
        assertThat(factory.getValidator().validate(request)).isEmpty();
        assertThat(request.email()).isEqualTo("user@example.com");
        assertThat(factory.getValidator().validate(new MemberUpdateRequest(
                "user@example.com", " ", LocalDate.now()))).isNotEmpty();
        assertThat(factory.getValidator().validate(new JobPositionUpdateRequest("A".repeat(101))))
                .isNotEmpty();
    }

    @Test
    void validatesCourseServiceBoundary() {
        var courses = mock(com.be.course.repository.CourseRepository.class);
        var members = mock(MemberRepository.class);
        var service = validated(new com.be.course.service.CourseService(courses, members,
                mock(com.be.assignment.service.AutoAssignmentService.class)));
        assertThatThrownBy(() -> service.create(new com.be.course.dto.CourseCreateRequest(
                " ", null, null, null, null, new java.math.BigDecimal("100.001"), 0L)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.changeStatus(1L, new com.be.course.dto.CourseStatusRequest(null)))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(courses, members);
    }

    @Test
    void validatesContentAndNestedOrderIdsBeforeRepositoryAccess() {
        var courses = mock(com.be.course.repository.CourseRepository.class);
        var contents = mock(com.be.course.repository.CourseContentRepository.class);
        var service = validated(new com.be.course.service.CourseContentService(courses, contents));
        assertThatThrownBy(() -> service.create(1L, new com.be.course.dto.CourseContentCreateRequest(
                " ", null, " ", -1, 0, null))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.reorder(1L,
                new com.be.course.dto.ContentOrderRequest(java.util.Arrays.asList(1L, null))))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(courses, contents);
    }

    @Test
    void validatesAssignmentRuleAndEnrollmentServiceRequests() {
        var rules = mock(com.be.assignment.repository.AssignmentRuleRepository.class);
        var courses = mock(com.be.course.repository.CourseRepository.class);
        var service = validated(new com.be.assignment.service.AssignmentRuleService(rules, courses,
                mock(DepartmentRepository.class), mock(JobPositionRepository.class),
                mock(com.be.assignment.service.AutoAssignmentService.class)));
        assertThatThrownBy(() -> service.create(1L, new com.be.assignment.dto.AssignmentRuleCreateRequest(
                com.be.assignment.enums.AssignmentRuleType.NEW_EMPLOYEE, null, null, 0, true)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.changeStatus(1L, 1L, new com.be.assignment.dto.AssignmentRuleStatusRequest(null)))
                .isInstanceOf(ConstraintViolationException.class);
        var enrollments = mock(com.be.enrollment.repository.EnrollmentRepository.class);
        var enrollmentService = validated(new com.be.enrollment.service.EnrollmentService(enrollments, courses,
                mock(MemberRepository.class), Clock.systemUTC(), mock(com.be.notification.service.NotificationService.class)));
        assertThatThrownBy(() -> enrollmentService.assignManually(1L, new com.be.enrollment.dto.ManualEnrollmentRequest(null)))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(rules, courses, enrollments);
    }

    @Test
    void validatesProgressBeforeRepositoryAccess() {
        var enrollments = mock(com.be.enrollment.repository.EnrollmentRepository.class);
        var contents = mock(com.be.course.repository.CourseContentRepository.class);
        var progresses = mock(com.be.enrollment.repository.ContentProgressRepository.class);
        var completion = mock(com.be.enrollment.service.EnrollmentCompletionService.class);
        var service = validated(new com.be.enrollment.service.ContentProgressService(
                enrollments, contents, progresses, completion, Clock.systemUTC(), mock(com.be.exam.repository.ExamRepository.class)));
        for (String rate : new String[] {"-1", "100.01", "33.333"}) {
            assertThatThrownBy(() -> service.update(1L, 1L, null,
                    new com.be.enrollment.dto.ContentProgressUpdateRequest(new java.math.BigDecimal(rate))))
                    .isInstanceOf(ConstraintViolationException.class);
        }
        assertThatThrownBy(() -> service.update(1L, 1L, null, null))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.update(1L, 1L, null,
                new com.be.enrollment.dto.ContentProgressUpdateRequest(null)))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(enrollments, contents, progresses, completion);
    }

    @Test
    void validatesExamManagementServiceBoundaries() {
        var courses = mock(com.be.course.repository.CourseRepository.class);
        var exams = mock(com.be.exam.repository.ExamRepository.class);
        var questions = mock(com.be.exam.repository.QuestionRepository.class);
        var choices = mock(com.be.exam.repository.QuestionChoiceRepository.class);
        var validator = new com.be.exam.service.ExamConfigurationValidator();
        var examService = validated(new com.be.exam.service.ExamService(courses, exams, questions, choices, validator, mock(com.be.exam.repository.ExamAttemptRepository.class)));
        for (String score : new String[] {"-1", "101", "50.001"}) {
            assertThatThrownBy(() -> examService.create(1L, new com.be.exam.dto.ExamCreateRequest(
                    "시험", new java.math.BigDecimal(score), 1))).isInstanceOf(ConstraintViolationException.class);
        }
        for (int max : new int[] {0, -1}) {
            assertThatThrownBy(() -> examService.create(1L, new com.be.exam.dto.ExamCreateRequest(
                    "시험", java.math.BigDecimal.TEN, max))).isInstanceOf(ConstraintViolationException.class);
        }
        var questionService = validated(new com.be.exam.service.QuestionService(examService, questions, choices,
                mock(com.be.exam.repository.ExamAttemptRepository.class), mock(com.be.exam.repository.ExamAnswerRepository.class), validator));
        for (String score : new String[] {"0", "-1", "100000", "10.001"}) {
            assertThatThrownBy(() -> questionService.create(1L, new com.be.exam.dto.QuestionCreateRequest("문제",
                    com.be.exam.enums.QuestionType.SINGLE_CHOICE, new java.math.BigDecimal(score), 1,
                    java.util.List.of(new com.be.exam.dto.ChoiceCreateRequest("A", true, 1)))))
                    .isInstanceOf(ConstraintViolationException.class);
        }
        assertThatThrownBy(() -> questionService.createChoice(1L, 1L,
                new com.be.exam.dto.ChoiceCreateRequest(" ", null, 0))).isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(courses, exams, questions, choices);
    }

    @SuppressWarnings("unchecked")
    private <T> T validated(T target) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor(factory.getValidator()));
        return (T) proxy.getProxy();
    }
}

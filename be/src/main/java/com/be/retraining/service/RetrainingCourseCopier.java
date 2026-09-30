package com.be.retraining.service;

import com.be.assignment.dto.AssignmentRuleCreateRequest;
import com.be.assignment.repository.AssignmentRuleRepository;
import com.be.assignment.service.AssignmentRuleService;
import com.be.course.entity.*;
import com.be.course.repository.*;
import com.be.coursework.entity.Assignment;
import com.be.coursework.repository.AssignmentRepository;
import com.be.exam.entity.*;
import com.be.exam.repository.*;
import com.be.exam.service.ExamConfigurationValidator;
import com.be.global.exception.*;
import com.be.member.enums.Role;
import com.be.member.repository.MemberRepository;
import com.be.retraining.entity.RetrainingPolicy;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

// 학습 결과 Repository를 의존하지 않는다. 호출자가 정책 → 원본 Course 잠금을 보유한다.
@Service @RequiredArgsConstructor @Transactional(propagation = Propagation.MANDATORY)
public class RetrainingCourseCopier {
    private final CourseRepository courses;
    private final CourseContentRepository contents;
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final QuestionChoiceRepository choices;
    private final AssignmentRepository assignments;
    private final AssignmentRuleRepository rules;
    private final AssignmentRuleService ruleService;
    private final ExamConfigurationValidator examValidator;
    private final MemberRepository members;

    public LocalDate shiftedDue(Course source, Assignment assignment, RetrainingPolicy.Dates dates) {
        if (source.getStartDate() == null) throw new BusinessException(ErrorCode.RETRAINING_SOURCE_INVALID);
        LocalDate due = dates.startDate().plusDays(ChronoUnit.DAYS.between(source.getStartDate(), assignment.getDueDate()));
        if (due.isBefore(dates.startDate()) || due.isAfter(dates.endDate()))
            throw new BusinessException(ErrorCode.RETRAINING_SOURCE_INVALID);
        return due;
    }

    public boolean overdue(Course source, RetrainingPolicy.Dates dates, LocalDate today) {
        if (dates.endDate().isBefore(today)) return true;
        if (source.getStartDate() == null) throw new BusinessException(ErrorCode.RETRAINING_SOURCE_INVALID);
        boolean overdue = false;
        for (var assignment : assignments.findByCourseIdOrderBySortOrderAsc(source.getId())) {
            LocalDate due = shiftedDue(source, assignment, dates);
            if (assignment.isRequired() && due.isBefore(today)) overdue = true;
        }
        return overdue;
    }

    public Course copy(RetrainingPolicy policy, Course source, int number) {
        var dates = policy.dates(number);
        var instructor = source.getInstructor();
        if (instructor != null) {
            instructor = members.findWithRolesById(instructor.getId()).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            if (!instructor.getRoles().contains(Role.INSTRUCTOR)) throw new BusinessException(ErrorCode.INVALID_COURSE_INSTRUCTOR);
        }
        var target = Course.create(policy.title(number), source.getDescription(), source.getCourseType(),
                dates.startDate(), dates.endDate(), source.getPassingProgressRate(), instructor);
        target.identifyOccurrence(policy, number);
        courses.saveAndFlush(target);
        for (var c : contents.findByCourseIdOrderBySortOrderAsc(source.getId())) {
            contents.save(CourseContent.create(target, c.getTitle(), c.getContentType(), c.getContentUrl(),
                    c.getDurationSeconds(), c.getSortOrder(), c.isRequired()));
        }
        for (var a : assignments.findByCourseIdOrderBySortOrderAsc(source.getId())) {
            assignments.save(Assignment.create(target, a.getTitle(), a.getDescription(), a.isRequired(),
                    shiftedDue(source, a, dates), a.getPassingScore(), a.getSortOrder()));
        }
        exams.findByCourseId(source.getId()).ifPresent(exam -> copyExam(exam, target, policy.isAutoOpen()));
        var sourceRules = rules.findByCourseIdOrderByIdAsc(source.getId());
        if (policy.isAutoOpen() && sourceRules.stream().noneMatch(r -> r.isActive()))
            throw new BusinessException(ErrorCode.RETRAINING_ACTIVE_RULE_REQUIRED);
        for (var r : sourceRules) {
            ruleService.create(target.getId(), new AssignmentRuleCreateRequest(r.getRuleType(),
                    r.getDepartment() == null ? null : r.getDepartment().getId(),
                    r.getJobPosition() == null ? null : r.getJobPosition().getId(),
                    r.getNewEmployeeDays() == null ? null : r.getNewEmployeeDays().intValue(), r.isActive()));
        }
        courses.flush();
        return target;
    }

    private void copyExam(Exam source, Course target, boolean validate) {
        var sourceQuestions = questions.findByExamIdOrderBySortOrderAsc(source.getId());
        var sourceChoices = choices.findByQuestionExamIdOrderByQuestionSortOrderAscSortOrderAsc(source.getId());
        if (validate) examValidator.validate(source, sourceQuestions, sourceChoices);
        var exam = exams.saveAndFlush(Exam.create(target, source.getTitle(), source.getPassingScore(), source.getMaxAttempts()));
        Map<Long, Question> mapping = new HashMap<>();
        for (var q : sourceQuestions) {
            mapping.put(q.getId(), questions.save(Question.create(exam, q.getQuestionText(), q.getQuestionType(), q.getScore(), q.getSortOrder())));
        }
        for (var c : sourceChoices) {
            choices.save(QuestionChoice.create(mapping.get(c.getQuestion().getId()), c.getChoiceText(), c.isCorrect(), c.getSortOrder()));
        }
    }
}

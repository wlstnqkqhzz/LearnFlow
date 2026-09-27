package com.be.coursework.repository;

import com.be.coursework.entity.AssignmentSubmission;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {
    @EntityGraph(attributePaths = {"assignment", "enrollment", "enrollment.member", "gradedBy"})
    Optional<AssignmentSubmission> findById(Long id);

    @Query("select s.enrollment.id from AssignmentSubmission s where s.id = :id")
    Optional<Long> findEnrollmentId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AssignmentSubmission s where s.assignment.id = :assignmentId and s.enrollment.id = :enrollmentId")
    Optional<AssignmentSubmission> findForUpdate(@Param("assignmentId") Long assignmentId, @Param("enrollmentId") Long enrollmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AssignmentSubmission s where s.id = :id")
    Optional<AssignmentSubmission> findForGrading(@Param("id") Long id);

    @EntityGraph(attributePaths = {"assignment", "gradedBy", "enrollment", "enrollment.member"})
    List<AssignmentSubmission> findByEnrollmentId(Long enrollmentId);

    @EntityGraph(attributePaths = {"assignment", "enrollment", "enrollment.member", "gradedBy"})
    @Query("""
        select s from AssignmentSubmission s where s.assignment.course.id = :courseId
        and (:assignmentId is null or s.assignment.id = :assignmentId)
        and (:enrollmentId is null or s.enrollment.id = :enrollmentId)
        and (:memberId is null or s.enrollment.member.id = :memberId)
        and (:status is null or (:status = 'PENDING_GRADING' and s.gradedAt is null)
            or (:status = 'PASSED' and s.gradedAt is not null and s.passed = true)
            or (:status = 'FAILED' and s.gradedAt is not null and s.passed = false))
        """)
    Page<AssignmentSubmission> search(@Param("courseId") Long courseId, @Param("assignmentId") Long assignmentId,
        @Param("enrollmentId") Long enrollmentId, @Param("memberId") Long memberId,
        @Param("status") String status, Pageable pageable);

    // 미제출도 포함: 필수 과제를 기준으로 합격 제출물이 없는 항목을 DB에서 검사한다.
    @Query("""
        select count(a) > 0 from Assignment a where a.course.id = :courseId and a.isRequired = true
        and not exists (select s.id from AssignmentSubmission s where s.assignment = a
            and s.enrollment.id = :enrollmentId and s.gradedAt is not null and s.passed = true)
        """)
    boolean hasUnsatisfiedRequired(@Param("courseId") Long courseId, @Param("enrollmentId") Long enrollmentId);

    @Query("""
        select count(a) > 0 from Assignment a where a.course.id = :courseId and a.isRequired = true
        and not exists (select s.id from AssignmentSubmission s where s.assignment = a
            and s.enrollment.id = :enrollmentId and (s.gradedAt is null or s.passed = true))
        """)
    boolean hasMissingOrFailedRequired(@Param("courseId") Long courseId, @Param("enrollmentId") Long enrollmentId);

    @Query("""
        select count(s) > 0 from AssignmentSubmission s
        where s.assignment.course.id = :courseId and s.assignment.isRequired = true
        and s.enrollment.id = :enrollmentId and s.gradedAt is null
        """)
    boolean hasPendingRequired(@Param("courseId") Long courseId, @Param("enrollmentId") Long enrollmentId);
}

package com.be.enrollment.repository;

import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

// 읽기 응답에 필요한 단일 연관관계만 함께 로드
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    @Override
    @EntityGraph(attributePaths = {"member", "course", "assignmentRule"})
    Optional<Enrollment> findById(Long id);

    // 부모 Course 잠금 후 현재 읽기로 중복 확인: RR의 오래된 스냅샷을 사용하지 않음
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.member.id = :memberId and e.course.id = :courseId")
    Optional<Enrollment> findExistingForAssignment(@Param("memberId") Long memberId, @Param("courseId") Long courseId);

    @EntityGraph(attributePaths = {"member", "course", "assignmentRule"})
    @Query("""
            select e from Enrollment e
            where (:courseId is null or e.course.id = :courseId)
              and (:memberId is null or e.member.id = :memberId)
              and (:status is null or e.status = :status)
            """)
    Page<Enrollment> search(@Param("courseId") Long courseId, @Param("memberId") Long memberId,
                            @Param("status") EnrollmentStatus status, Pageable pageable);
}

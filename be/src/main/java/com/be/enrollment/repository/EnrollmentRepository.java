package com.be.enrollment.repository;

import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

// 읽기 응답에 필요한 단일 연관관계만 함께 로드
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    // 상태 변경으로 조회 집합이 줄어도 누락되지 않도록 ID 기반으로 다음 묶음을 조회
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Query("""
            select e.id from Enrollment e
            where e.status in :statuses and e.dueDate < :today and e.id > :afterId
            order by e.id
            """)
    List<Long> findOverdueIds(@Param("statuses") List<EnrollmentStatus> statuses,
                             @Param("today") LocalDate today, @Param("afterId") Long afterId,
                             Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"member", "course", "assignmentRule"})
    Optional<Enrollment> findById(Long id);

    @Query("select e.course.id from Enrollment e where e.id = :id")
    Optional<Long> findCourseId(@Param("id") Long id);

    // 시험 변경을 직렬화하며 동시에 진행 중인 진도 요청의 낙관적 버전도 무효화
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("select e from Enrollment e where e.id = :id")
    Optional<Enrollment> findForExamUpdate(@Param("id") Long id);

    // 서로 다른 콘텐츠의 동시 수정도 동일 수강 버전으로 충돌 감지하여 수료 판정 누락 방지
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @EntityGraph(attributePaths = {"member", "course"})
    @Query("select e from Enrollment e where e.id = :id")
    Optional<Enrollment> findForProgressUpdate(@Param("id") Long id);

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

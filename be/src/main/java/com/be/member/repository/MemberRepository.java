package com.be.member.repository;

import com.be.member.entity.Member;
import com.be.member.enums.MemberStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 회원 식별값 중복 검사
public interface MemberRepository extends JpaRepository<Member, Long> {
    // 규칙별 후보를 현재 읽기로 조회·잠금: ACTIVE 및 직속 소속/입사일 범위만 사용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from Member m
            where m.status = com.be.member.enums.MemberStatus.ACTIVE
              and (:departmentId is null or m.department.id = :departmentId)
              and (:jobPositionId is null or m.jobPosition.id = :jobPositionId)
              and (:hireFrom is null or m.hireDate >= :hireFrom)
              and (:hireTo is null or m.hireDate <= :hireTo)
            order by m.id
            """)
    List<Member> findAssignmentCandidates(@Param("departmentId") Long departmentId,
            @Param("jobPositionId") Long jobPositionId, @Param("hireFrom") LocalDate hireFrom,
            @Param("hireTo") LocalDate hireTo);

    // 인증에서는 트랜잭션 안에서 역할까지 함께 조회
    @EntityGraph(attributePaths = "roles")
    Optional<Member> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findWithRolesById(@Param("id") Long id);

    // 페이지 조회 후 해당 회원들의 역할만 일괄 로딩 (컬렉션 fetch와 pagination을 분리)
    @EntityGraph(attributePaths = "roles")
    List<Member> findWithRolesByIdIn(List<Long> ids);

    boolean existsByEmployeeNumber(String employeeNumber);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);

    // 상태·역할·정보 변경을 직렬화하여 퇴사 최종 상태 및 역할 변경 결과 보호
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    // 선택 검색 조건과 페이징 - 이름의 SQL 와일드카드는 Service에서 이스케이프
    @Query("""
            select m from Member m
            where (:namePattern is null or lower(m.name) like lower(:namePattern) escape '!')
              and (:departmentId is null or m.department.id = :departmentId)
              and (:jobPositionId is null or m.jobPosition.id = :jobPositionId)
              and (:status is null or m.status = :status)
            """)
    Page<Member> search(@Param("namePattern") String namePattern,
                        @Param("departmentId") Long departmentId,
                        @Param("jobPositionId") Long jobPositionId,
                        @Param("status") MemberStatus status,
                        Pageable pageable);
}

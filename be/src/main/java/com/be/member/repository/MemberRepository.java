package com.be.member.repository;

import com.be.member.entity.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 회원 식별값 중복 검사
public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByEmployeeNumber(String employeeNumber);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);

    // 상태·역할·정보 변경을 직렬화하여 퇴사 최종 상태 및 역할 변경 결과 보호
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);
}

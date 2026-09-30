package com.be.retraining.repository;

import com.be.retraining.entity.RetrainingPolicy;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RetrainingPolicyRepository extends JpaRepository<RetrainingPolicy, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RetrainingPolicy p where p.id = :id")
    Optional<RetrainingPolicy> findForUpdate(@Param("id") Long id);

    @Query("select p.id from RetrainingPolicy p where p.enabled = true and p.autoCreate = true and p.nextGenerationDate <= :today and p.id > :after order by p.id")
    List<Long> findDueIds(@Param("today") LocalDate today, @Param("after") Long after, Pageable pageable);
}

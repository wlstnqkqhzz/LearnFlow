package com.be.enrollment.service;

import com.be.enrollment.enums.EnrollmentStatus;
import com.be.enrollment.repository.EnrollmentRepository;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 서울 날짜를 한 번 확정한 후 과거의 미처리 수강까지 순차 만료
@Service
@RequiredArgsConstructor
public class EnrollmentExpirationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final List<EnrollmentStatus> TARGET_STATUSES =
            List.of(EnrollmentStatus.ASSIGNED, EnrollmentStatus.IN_PROGRESS);
    private static final int BATCH_SIZE = 500;
    private final EnrollmentRepository enrollments;
    private final EnrollmentExpirationProcessor processor;
    private final Clock clock;

    // 전체 실행을 하나의 트랜잭션으로 묶지 않으며 건별 커밋이 끝난 뒤에만 성공 건수 집계
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExpirationResult expireOverdueEnrollments() {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        long afterId = 0;
        long expiredCount = 0;
        long conflictCount = 0;
        while (true) {
            var ids = enrollments.findOverdueIds(TARGET_STATUSES, today, afterId, PageRequest.of(0, BATCH_SIZE));
            if (ids.isEmpty()) break;
            for (Long id : ids) {
                try {
                    if (processor.expire(id, today)) expiredCount++;
                } catch (ConcurrencyFailureException exception) {
                    // 실패한 트랜잭션이 종료된 뒤 처리: 재시도 없이 다음 스케줄에서 다시 평가
                    conflictCount++;
                }
            }
            afterId = ids.getLast();
        }
        return new ExpirationResult(expiredCount, conflictCount);
    }

    // 개인정보 없이 실행 결과만 스케줄러에 전달
    public record ExpirationResult(long expiredCount, long conflictCount) {}
}

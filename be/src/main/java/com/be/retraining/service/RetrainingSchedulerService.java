package com.be.retraining.service;

import com.be.global.exception.BusinessException;
import com.be.retraining.repository.RetrainingPolicyRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Slf4j @Service @RequiredArgsConstructor
public class RetrainingSchedulerService {
    private final RetrainingPolicyRepository policies;
    private final RetrainingOccurrenceService occurrences;
    private final Clock clock;
    public record BatchResult(int created, int deferred, int failed) {}

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BatchResult runDue() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        long after = 0;
        int created = 0, deferred = 0, failed = 0;
        while (true) {
            var ids = policies.findDueIds(today, after, PageRequest.of(0, 100));
            if (ids.isEmpty()) break;
            for (Long id : ids) {
                try {
                    var result = occurrences.generateAutomatically(id, today);
                    if (result != null && result.created()) created++;
                    else deferred++;
                } catch (BusinessException exception) {
                    deferred++;
                    log.warn("Retraining deferred. policyId={}, code={}", id, exception.getErrorCode());
                } catch (RuntimeException exception) {
                    failed++;
                    // 다른 정책은 계속 처리. 롤백된 동일 회차를 다음 스케줄에서 재시도한다.
                    log.error("Retraining failed. policyId={}", id, exception);
                }
            }
            after = ids.getLast();
        }
        return new BatchResult(created, deferred, failed);
    }
}

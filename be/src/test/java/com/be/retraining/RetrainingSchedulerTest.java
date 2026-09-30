package com.be.retraining;

import com.be.global.exception.*;
import com.be.retraining.repository.RetrainingPolicyRepository;
import com.be.retraining.service.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetrainingSchedulerTest {
    @Test void failedTransactionDoesNotStopOtherPoliciesAndSeoulDateIsUsed() {
        var policies = mock(RetrainingPolicyRepository.class);
        var occurrences = mock(RetrainingOccurrenceService.class);
        var clock = Clock.fixed(Instant.parse("2026-09-29T15:30:00Z"), ZoneOffset.UTC);
        var today = LocalDate.of(2026, 9, 30);
        when(policies.findDueIds(eq(today), eq(0L), any())).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(occurrences.generateAutomatically(1L, today)).thenThrow(new org.springframework.dao.CannotAcquireLockException("conflict"));
        when(occurrences.generateAutomatically(2L, today)).thenThrow(new BusinessException(ErrorCode.RETRAINING_OVERDUE));
        when(occurrences.generateAutomatically(3L, today)).thenReturn(new RetrainingOccurrenceService.Result(null, true));
        var result = new RetrainingSchedulerService(policies, occurrences, clock).runDue();
        assertThat(result).isEqualTo(new RetrainingSchedulerService.BatchResult(1, 2, 1));
        verify(occurrences).generateAutomatically(4L, today);
        verify(policies).findDueIds(eq(today), eq(4L), any());
    }
}

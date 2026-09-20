package com.be.enrollment;

import com.be.enrollment.scheduler.EnrollmentExpirationScheduler;
import com.be.enrollment.service.EnrollmentExpirationService;
import com.be.global.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 시간 대기 없이 서비스 위임과 서울 자정 스케줄 설정 검증
class EnrollmentExpirationSchedulerTest {
    @Test
    void delegatesToServiceOnce() {
        var service = mock(EnrollmentExpirationService.class);
        when(service.expireOverdueEnrollments()).thenReturn(new EnrollmentExpirationService.ExpirationResult(3, 1));
        new EnrollmentExpirationScheduler(service).expireOverdueEnrollments();
        verify(service).expireOverdueEnrollments();
        verifyNoMoreInteractions(service);
    }

    @Test
    void runsAtSeoulMidnightAndSchedulingIsEnabled() throws Exception {
        var scheduled = EnrollmentExpirationScheduler.class.getMethod("expireOverdueEnrollments").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        assertThat(SchedulingConfig.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }
}

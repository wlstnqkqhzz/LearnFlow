package com.be.enrollment.scheduler;

import com.be.enrollment.service.EnrollmentExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 단일 인스턴스 기준 매일 서울 자정에 실행; 비즈니스 처리는 서비스에 위임
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentExpirationScheduler {
    private final EnrollmentExpirationService service;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void expireOverdueEnrollments() {
        var result = service.expireOverdueEnrollments();
        log.info("Enrollment expiration completed. expiredCount={}, conflictCount={}",
                result.expiredCount(), result.conflictCount());
    }
}

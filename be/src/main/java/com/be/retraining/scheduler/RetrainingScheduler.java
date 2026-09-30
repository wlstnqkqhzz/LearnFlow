package com.be.retraining.scheduler;

import com.be.retraining.service.RetrainingSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
public class RetrainingScheduler {
    private final RetrainingSchedulerService service;
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void generateDueCourses() {
        var result = service.runDue();
        log.info("Retraining completed. created={}, deferred={}, failed={}", result.created(), result.deferred(), result.failed());
    }
}

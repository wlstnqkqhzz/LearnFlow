package com.be.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 서버 내부 정기 작업 활성화 (외부 실행 API나 분산 스케줄러는 제공하지 않음)
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}

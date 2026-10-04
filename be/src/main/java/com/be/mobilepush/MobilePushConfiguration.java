package com.be.mobilepush;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MobilePushProperties.class)
@EnableScheduling
public class MobilePushConfiguration {
    @Bean(destroyMethod = "close") MobilePushTasks mobilePushTasks() { return new MobilePushTasks(); }
    @Bean(destroyMethod = "close") ExpoPushTransport expoPushTransport(MobilePushProperties properties, ObjectMapper mapper) {
        return new HttpExpoPushTransport(properties, mapper);
    }
}

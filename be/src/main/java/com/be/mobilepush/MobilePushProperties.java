package com.be.mobilepush;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mobile-push")
public record MobilePushProperties(boolean enabled, String accessToken) {
    @Override public String toString() { return "MobilePushProperties[redacted]"; }
}

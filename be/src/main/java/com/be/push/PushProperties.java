package com.be.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("push")
public record PushProperties(boolean enabled, String publicKey, String privateKey, String subject) {
    @Override public String toString() { return "PushProperties[enabled=" + enabled + ", credentials=REDACTED]"; }
}

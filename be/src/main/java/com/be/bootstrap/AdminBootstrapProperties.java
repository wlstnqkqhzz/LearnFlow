package com.be.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("learnflow.bootstrap.admin")
public record AdminBootstrapProperties(boolean enabled, String email, String password, String name) {
    @Override public String toString() { return "AdminBootstrapProperties[REDACTED]"; }
}

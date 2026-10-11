package com.be.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminBootstrapProperties.class)
@ConditionalOnProperty(prefix = "learnflow.bootstrap.admin", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminBootstrapService service;
    private final AdminBootstrapProperties properties;

    @Override public void run(ApplicationArguments args) {
        try {
            boolean created;
            try { created = service.create(properties); }
            catch (DataIntegrityViolationException | AdminBootstrapService.Failure conflict) {
                // A competing startup may have committed since our initial read.
                if (!service.adminExists()) {
                    if (conflict instanceof AdminBootstrapService.Failure failure) throw failure;
                    throw new AdminBootstrapService.Failure("Initial admin bootstrap database conflict; no account was changed");
                }
                created = false;
            }
            log.info(created ? "Initial admin created" : "Admin already exists; bootstrap skipped");
        } catch (AdminBootstrapService.Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            // Do not attach database/encoder exceptions that may carry credentials or personal data.
            throw new IllegalStateException("Initial admin bootstrap failed; check database availability and configuration");
        }
    }
}

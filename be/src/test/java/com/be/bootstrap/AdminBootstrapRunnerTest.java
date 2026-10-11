package com.be.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapRunnerTest {
    final AdminBootstrapService service = mock(AdminBootstrapService.class);
    final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(AdminBootstrapService.class, () -> service)
            .withUserConfiguration(AdminBootstrapRunner.class);
    @Test void startupHookAbsentByDefault() {
        context.run(c -> assertThat(c).doesNotHaveBean(AdminBootstrapRunner.class));
        verifyNoInteractions(service);
    }
    @Test void explicitFalseDoesNotRegisterStartupHook() {
        context.withPropertyValues("learnflow.bootstrap.admin.enabled=false")
                .run(c -> assertThat(c).doesNotHaveBean(AdminBootstrapRunner.class));
        verifyNoInteractions(service);
    }
    @Test void enabledRegistersStartupHookAndBindsProperties() {
        context.withPropertyValues("learnflow.bootstrap.admin.enabled=true", "learnflow.bootstrap.admin.name=Initial")
                .run(c -> {
                    assertThat(c).hasSingleBean(AdminBootstrapRunner.class);
                    assertThat(c.getBean(AdminBootstrapProperties.class).name()).isEqualTo("Initial");
                    c.getBean(AdminBootstrapRunner.class).run(null);
                    verify(service).create(c.getBean(AdminBootstrapProperties.class));
                });
    }
    @Test void unexpectedExceptionsDoNotExposeCredentials() {
        var input = new AdminBootstrapProperties(true, "fixture@example.test", "fixture-password", "Initial");
        when(service.create(input)).thenThrow(new IllegalStateException(input.password()));
        assertThatThrownBy(() -> new AdminBootstrapRunner(service, input).run(null))
                .hasMessage("Initial admin bootstrap failed; check database availability and configuration")
                .hasNoCause();
    }
}

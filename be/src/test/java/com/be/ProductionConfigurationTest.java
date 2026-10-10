package com.be;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Real prod configuration/security wiring; external DB/Redis are mocked by the base smoke test.
// Schema validation requires the deployed MySQL schema and is deliberately not claimed here.
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
    "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=never",
    "management.health.db.enabled=false", "management.health.redis.enabled=false",
    "APP_DATASOURCE_URL=jdbc:mysql://localhost/test", "APP_DATASOURCE_USERNAME=test",
    "APP_DATASOURCE_PASSWORD=", "REDIS_HOST=localhost"
})
class ProductionConfigurationTest extends BeApplicationTests {
    @Autowired MockMvc mvc;
    @Test void anonymousHealthExposesOnlyAggregateStatus() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\"}"))
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist());
    }
    @Test void otherActuatorPathsAndBusinessApisRemainProtected() throws Exception {
        for (String path : new String[]{"/actuator/env", "/actuator/info", "/actuator/health/db", "/api/admin/dashboard", "/api/mobile/push/subscriptions/1"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
    }
    @Test void productionPoliciesAreExplicitWithoutTestOverrides() throws Exception {
        var properties = new YamlPropertySourceLoader().load("prod", new ClassPathResource("application-prod.yaml")).getFirst();
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(properties.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(properties.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${APP_DATASOURCE_PASSWORD}");
    }
}

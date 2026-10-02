package com.be.statistics;

import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// Run the same boundary/population tests on MySQL without touching the existing application schema.
// Opt-in requires CREATE/DROP DATABASE privileges; only the schema created by this test is dropped.
@EnabledIfSystemProperty(named="statistics.mysql-test",matches="true")
class StatisticsMysqlTest extends StatisticsIntegrationTest {
    private JdbcTemplate admin;
    private String ownedSchema;
    @Override protected DataSource createSource() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application-local.yaml"));
        var properties = Objects.requireNonNull(yaml.getObject());
        String url = properties.getProperty("spring.datasource.url");
        String username = properties.getProperty("spring.datasource.username");
        String password = properties.getProperty("spring.datasource.password");
        admin = new JdbcTemplate(new DriverManagerDataSource(url,username,password));
        String candidate = "learnflow_statistics_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE `" + candidate + "`");
        ownedSchema = candidate;
        // Replace the database name only, preserving any connection options.
        int query = url.indexOf('?');
        String base = query < 0 ? url : url.substring(0,query);
        String options = query < 0 ? "" : url.substring(query);
        String testUrl = base.substring(0,base.lastIndexOf('/')+1) + ownedSchema + options;
        return new DriverManagerDataSource(testUrl,username,password);
    }
    @Override protected void closeSource() {
        if (ownedSchema != null) {
            if (!ownedSchema.matches("learnflow_statistics_test_[0-9a-f]{32}")) throw new IllegalStateException("Invalid test schema");
            admin.execute("DROP DATABASE `" + ownedSchema + "`");
            ownedSchema = null;
        }
    }
}

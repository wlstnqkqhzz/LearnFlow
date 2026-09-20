package com.be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// 외부 DB/Redis 없이 실제 Bean 연결·JPA 매핑·Repository 생성만 검증하는 부팅 스모크 테스트
@SpringBootTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=never",
        "spring.jpa.open-in-view=false"
})
class BeApplicationTests {
    @MockitoBean DataSource dataSource;
    @MockitoBean LettuceConnectionFactory redisConnectionFactory;
    @Autowired EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void testJwt(DynamicPropertyRegistry properties) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        properties.add("jwt.secret", () -> Base64.getEncoder().encodeToString(key));
        properties.add("jwt.access-token-ttl-seconds", () -> 600);
        properties.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }

    @Test
    void contextLoads() throws Exception {
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(entityManagerFactory.getMetamodel().getEntities()).hasSize(13);
        // 래퍼/health 구성 조회는 허용하되 실제 저장소 연결을 요청하지 않았는지 확인
        verify(dataSource, never()).getConnection();
        verify(dataSource, never()).getConnection(anyString(), anyString());
        verify(redisConnectionFactory, never()).getConnection();
        verify(redisConnectionFactory, never()).getReactiveConnection();
    }

}

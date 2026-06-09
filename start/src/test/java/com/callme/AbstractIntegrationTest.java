package com.callme;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for every integration test that needs the full application context
 * backed by a real PostgreSQL instance. The container is static — one per JVM run,
 * shared across all subclasses — so all 10 Flyway migrations run once and the
 * Spring context is cached across test methods/classes by the standard
 * {@link org.springframework.test.context.TestContext} caching mechanism.
 *
 * <p>{@code @ServiceConnection} tells Spring Boot to override the datasource URL,
 * username, and password with the container's actual values, regardless of what
 * {@code application-dev.yml} may have set (the {@code test} profile suppresses
 * {@code dev} anyway via {@code @ActiveProfiles}). No {@code @DynamicPropertySource}
 * boilerplate needed — this is the Spring Boot 3.1+/4.x idiomatic pattern.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("callme")
                    .withUsername("callme")
                    .withPassword("callme");
}

package com.callme;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every integration test that needs the full application context
 * backed by a real PostgreSQL instance, with all Flyway migrations applied.
 *
 * <p>Deliberately the <em>singleton-container</em> pattern (manual {@code start()}
 * in a static initializer, NO {@code @Testcontainers}/{@code @Container}): the JUnit
 * Testcontainers extension stops static {@code @Container} fields after EACH test
 * class, while Spring's TestContext framework caches the application context (and
 * its Hikari pool) ACROSS classes — so with two or more subclasses, every class
 * after the first inherited a cached context pointing at a container the previous
 * class had already killed ("Connection is not available... total=0"). Started once
 * here, the container lives for the whole JVM run and Ryuk reaps it on exit.
 *
 * <p>{@code @ServiceConnection} tells Spring Boot to override the datasource URL,
 * username, and password with the container's actual values, regardless of what
 * {@code application-dev.yml} may have set (the {@code test} profile suppresses
 * {@code dev} anyway via {@code @ActiveProfiles}).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("callme")
                    .withUsername("callme")
                    .withPassword("callme");

    static {
        POSTGRES.start();
    }
}

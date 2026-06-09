package com.callme;

import org.junit.jupiter.api.Test;

class CallmeApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Verifies that all modules wire together (JPA mappings, Flyway migrations,
        // Spring Security, port/adapter bindings) against a real Postgres instance
        // provided by AbstractIntegrationTest — the same posture as CI and prod.
    }

}

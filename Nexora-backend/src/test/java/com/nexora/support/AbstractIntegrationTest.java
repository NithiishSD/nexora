package com.nexora.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Base class for every test that needs the full application and a real database.
// Extend it:  class MyTest extends AbstractIntegrationTest { ... }
//
// A real PostgreSQL 17 runs in a throwaway Docker container (never H2 and never your dev
// database): the same engine, locks and constraints as production.
@SpringBootTest
public abstract class AbstractIntegrationTest {

    // static + started once = the "singleton container" pattern. All test classes share one
    // container (fast), and Spring can cache the application context between them.
    // Testcontainers' Ryuk helper container removes it when the JVM exits.
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    protected static final java.nio.file.Path DOCUMENTS_DIR;

    static {
        POSTGRES.start();
        try {
            DOCUMENTS_DIR = java.nio.file.Files.createTempDirectory("nexora-documents");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // Runs before the application context starts. It points spring.datasource.* at the
    // container (random host port), overriding application.yml. Flyway then migrates the empty
    // database on startup, so the tests also prove the migrations work from scratch.
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // the prod profile's ${FLYWAY_*} placeholders (a separate migration user there); other profiles never read them,
        // so Flyway keeps using each test's own datasource
        registry.add("FLYWAY_URL", POSTGRES::getJdbcUrl);
        registry.add("FLYWAY_USER", POSTGRES::getUsername);
        registry.add("FLYWAY_PASSWORD", POSTGRES::getPassword);
        // uploaded documents go to a throw-away directory, never into the working tree
        registry.add("nexora.documents.storage-root", () -> DOCUMENTS_DIR.toString());
        // the suite sends hundreds of requests a minute as one owner from one address: the shared limiter is off here;
        // RateLimitApiTest drives its own limiter instance at the real limits
        registry.add("nexora.rate-limit.enabled", () -> "false");
        // Spring Boot switches metrics export off in tests; MetricsApiTest checks the Prometheus endpoint
        registry.add("management.prometheus.metrics.export.enabled", () -> "true");
    }
}

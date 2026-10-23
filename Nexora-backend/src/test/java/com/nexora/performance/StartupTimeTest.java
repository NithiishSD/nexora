package com.nexora.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ConfigurableApplicationContext;

import com.nexora.NexoraApplication;
import com.nexora.support.AbstractIntegrationTest;

// NFR-PERF-08: on an empty database the application migrates and is healthy within 60 s; the migrations alone take
// at most 30 s. A real application start (its own context, random port) against a fresh database.
@Tag("performance")
class StartupTimeTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("NFR-PERF-08 empty database migrates and starts within60 seconds")
    void emptyDatabase_migratesAndStartsWithin60Seconds() throws Exception {
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement sql = admin.createStatement()) {
            sql.execute("DROP DATABASE IF EXISTS startup_check");
            sql.execute("CREATE DATABASE startup_check");
            sql.execute("DROP DATABASE IF EXISTS migration_check");
            sql.execute("CREATE DATABASE migration_check");
        }
        // the migrations on their own
        long migrationStart = System.nanoTime();
        Flyway.configure().dataSource(url("migration_check"), POSTGRES.getUsername(), POSTGRES.getPassword()).load()
                .migrate();
        Duration migration = Duration.ofNanos(System.nanoTime() - migrationStart);

        // the whole application, from nothing to a healthy state
        long start = System.nanoTime();
        try (ConfigurableApplicationContext app = new SpringApplicationBuilder(NexoraApplication.class).run(
                "--server.port=0", "--nexora.management.port=0",
                "--spring.datasource.url=" + url("startup_check"),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword())) {
            Status status = app.getBean(HealthEndpoint.class).health().getStatus();
            Duration startup = Duration.ofNanos(System.nanoTime() - start);
            System.out.println("PERF migrations " + migration.toMillis() + " ms, start-up " + startup.toMillis() + " ms");
            assertThat(status).isEqualTo(Status.UP);
            assertThat(startup).isLessThanOrEqualTo(Duration.ofSeconds(60));
            assertThat(migration).isLessThanOrEqualTo(Duration.ofSeconds(30));
        }
    }

    private static String url(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }
}

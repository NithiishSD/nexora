package com.nexora.shared.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Activates real PostgreSQL 17 embedded in-process when running in the 'standalone' profile.
 * Enables the complete application to run 100% locally on an end-user's desktop without requiring Docker
 * or an external database installation.
 */
@Configuration
@Profile("standalone")
public class StandaloneEmbeddedPostgresConfig {

    private static final Logger log = LoggerFactory.getLogger(StandaloneEmbeddedPostgresConfig.class);
    private static volatile EmbeddedPostgres embeddedPostgresInstance;

    @Value("${smartsilk.embedded.postgres.port:5438}")
    private int postgresPort;

    @Value("${smartsilk.data.dir:}")
    private String customDataDir;

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        if (embeddedPostgresInstance != null) {
            return embeddedPostgresInstance;
        }
        Path dataDir = resolveDataDirectory();
        Files.createDirectories(dataDir);

        log.info("Starting standalone embedded PostgreSQL 17 engine in: {} on port {}", dataDir, postgresPort);
        embeddedPostgresInstance = EmbeddedPostgres.builder()
                .setCleanDataDirectory(false)
                .setDataDirectory(dataDir)
                .setPort(postgresPort)
                .setPGStartupWait(Duration.ofSeconds(20))
                .start();

        log.info("Standalone embedded PostgreSQL 17 successfully started on port {}", postgresPort);
        return embeddedPostgresInstance;
    }

    @Bean
    @Primary
    @DependsOn("embeddedPostgres")
    public DataSource dataSource(EmbeddedPostgres pg) throws SQLException {
        // Ensure the nexora_db database exists
        try (Connection conn = pg.getPostgresDatabase().getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'nexora_db'");
            if (!rs.next()) {
                log.info("Database 'nexora_db' not found. Creating 'nexora_db' in embedded PostgreSQL...");
                stmt.execute("CREATE DATABASE nexora_db");
                log.info("Database 'nexora_db' created successfully.");
            }
        }

        String jdbcUrl = pg.getJdbcUrl("postgres", "nexora_db");
        log.info("Configuring standalone DataSource pointing to: {}", jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(5000);
        config.setPoolName("SmartSilkStandaloneHikariPool");

        return new HikariDataSource(config);
    }

    private Path resolveDataDirectory() {
        if (customDataDir != null && !customDataDir.trim().isEmpty()) {
            return Paths.get(customDataDir.trim());
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Paths.get(localAppData, "SmartSilk", "data", "postgres");
            }
            return Paths.get(System.getProperty("user.home"), "AppData", "Local", "SmartSilk", "data", "postgres");
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "SmartSilk", "data", "postgres");
        } else {
            // Linux / Unix
            String xdgData = System.getenv("XDG_DATA_HOME");
            if (xdgData != null && !xdgData.isBlank()) {
                return Paths.get(xdgData, "smartsilk", "data", "postgres");
            }
            return Paths.get(System.getProperty("user.home"), ".local", "share", "smartsilk", "data", "postgres");
        }
    }
}

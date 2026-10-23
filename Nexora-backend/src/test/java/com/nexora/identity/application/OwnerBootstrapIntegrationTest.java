package com.nexora.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.nexora.support.AbstractIntegrationTest;

// The application is started against a brand-new, EMPTY database (a second database inside the shared
// test container), with bootstrap variables set. OwnerBootstrap runs during that startup.
@TestPropertySource(properties = {
        "nexora.bootstrap.owner-username=FirstOwner",
        "nexora.bootstrap.owner-password=First-Owner-Pass-2026"})
class OwnerBootstrapIntegrationTest extends AbstractIntegrationTest {

    private static final String EMPTY_DB = "bootstrap_empty";

    @Autowired
    OwnerBootstrap ownerBootstrap;

    @Autowired
    JdbcTemplate jdbc;

    // The base class registers spring.datasource.url for the shared database. The Hikari pool's own
    // jdbc-url is bound after that URL, so setting it points this test class (only) at the empty database.
    @DynamicPropertySource
    static void emptyDatabase(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + EMPTY_DB);
            statement.execute("CREATE DATABASE " + EMPTY_DB);
        }
        registry.add("spring.datasource.hikari.jdbc-url", () -> POSTGRES.getJdbcUrl().replace("/test?", "/" + EMPTY_DB + "?")
                .replaceFirst("/test$", "/" + EMPTY_DB));
    }

    @Test
    @DisplayName("FR-AUTH-10 empty users table with environment variables creates owner")
    void emptyUsersTable_withEnvironmentVariables_createsOwner() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo(EMPTY_DB);
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from users u
                join user_roles ur on ur.user_id = u.id
                join roles r on r.id = ur.role_id
                where u.username = 'firstowner' and u.is_active and r.name = 'OWNER'
                  and u.password_hash like '{bcrypt}%' and u.created_by is null
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-AUTH-10 existing users skips bootstrap")
    void existingUsers_skipsBootstrap() {
        boolean created = ownerBootstrap.createOwnerIfNoUsers("someone-else", "Another-Pass-2026");

        assertThat(created).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select username from users", String.class)).isEqualTo("firstowner");
    }

    @Test
    @DisplayName("FR-AUTH-10 weak bootstrap password is rejected")
    void weakBootstrapPassword_isRejected() {
        assertThatThrownBy(() -> OwnerBootstrap.checkPasswordPolicy("owner", "short1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OwnerBootstrap.checkPasswordPolicy("owner", "only-letters-here"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OwnerBootstrap.checkPasswordPolicy("owner1234567", "OWNER1234567"))
                .isInstanceOf(IllegalStateException.class);
        OwnerBootstrap.checkPasswordPolicy("owner", "DevOwner#2026pass");   // the dev default passes
    }
}

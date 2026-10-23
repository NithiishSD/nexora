package com.nexora.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

import com.nexora.support.AbstractIntegrationTest;

// NFR-SEC-08 with the real production pieces: the role script of deploy/postgres runs on a fresh database, Flyway
// migrates it as nexora_owner, and nexora_app (the application's user) is then refused every schema change and every
// UPDATE/DELETE/TRUNCATE of a ledger row, while normal reads and writes still work.
class DatabaseRoleIntegrationTest extends AbstractIntegrationTest {

    private static final String DB = "roles_check";
    private static final String OWNER_PASSWORD = "owner-test-pw";
    private static final String APP_PASSWORD = "app-test-pw";

    @Test
    @DisplayName("NFR-SEC-08 app role cannot update or delete ledger rows")
    void appRole_cannotUpdateOrDeleteLedgerRows() throws Exception {
        exec("psql", "-U", POSTGRES.getUsername(), "-d", "postgres", "-c", "CREATE DATABASE " + DB);
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("../deploy/postgres/10-roles.sh")),
                "/tmp/10-roles.sh");
        exec("bash", "-c", "POSTGRES_USER=" + POSTGRES.getUsername() + " POSTGRES_DB=" + DB
                + " OWNER_DB_PASSWORD=" + OWNER_PASSWORD + " APP_DB_PASSWORD=" + APP_PASSWORD + " bash /tmp/10-roles.sh");
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + DB;
        Flyway.configure().dataSource(url, "nexora_owner", OWNER_PASSWORD).load().migrate();

        try (Connection app = DriverManager.getConnection(url, "nexora_app", APP_PASSWORD);
             Statement sql = app.createStatement()) {
            // the application's normal work is allowed
            sql.executeQuery("select count(*) from financial_transactions").close();
            sql.executeUpdate("update parties set name = name where false");
            // ledger rows can never be changed, deleted or truncated
            for (String statement : new String[] {
                    "update financial_transactions set description = 'x'",
                    "delete from material_movements",
                    "delete from audit_log",
                    "update customer_payment_allocations set allocated_amount = 0",
                    "truncate audit_log"}) {
                assertRefused(sql, statement);
            }
            // AC1: no schema changes either
            assertRefused(sql, "drop table parties");
            assertRefused(sql, "create table intruder (id int)");
            assertRefused(sql, "alter table payments add column note text");
        }
    }

    private static void assertRefused(Statement sql, String statement) {
        assertThatThrownBy(() -> sql.execute(statement)).as(statement).isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).as(statement).isEqualTo("42501"));
    }

    private static void exec(String... command) throws Exception {
        ExecResult result = POSTGRES.execInContainer(command);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }
}

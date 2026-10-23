package com.nexora.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;

// The Testcontainers database starts empty, Flyway applies every migration, and Hibernate then
// validates every entity against the real tables (ddl-auto=validate). If this context starts, the
// migrations and the entity mappings agree.
class MigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("NFR-TEST-07 NFR-REL-07 all migrations on empty database validate against entities")
    void allMigrations_onEmptyDatabase_validateAgainstEntities() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(Arrays.stream(applied).allMatch(m -> m.getState().isApplied() && !m.getState().isFailed()))
                .isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        // the identity entities are part of the validated mapping
        assertThat(entityManagerFactory.getMetamodel().getEntities()).extracting(EntityType::getName)
                .contains("User", "Role", "RefreshToken");
    }

    @Test
    @Transactional   // rolled back after the test: nothing is left in the shared database
    @DisplayName("FR-PARTY-16 party roles check accepts lender")
    void partyRolesCheck_acceptsLender() {
        UUID partyId = insertParty();

        jdbc.update("insert into party_roles (party_id, role) values (?, 'LENDER')", partyId);

        assertThat(jdbc.queryForObject("select count(*) from party_roles where party_id = ?", Integer.class, partyId))
                .isEqualTo(1);
        // the CHECK constraint still rejects anything outside the five roles
        assertThatThrownBy(() -> jdbc.update("insert into party_roles (party_id, role) values (?, 'ADMIN')", partyId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("NFR-REL-07 migrations apply on data from previous release")
    void migrationsApplyOnDataFromPreviousRelease() throws Exception {
        // a database at the schema of the previous build (up to V14, before this release's V15/V16), holding data
        try (Connection admin = connect("test"); Statement sql = admin.createStatement()) {
            sql.execute("DROP DATABASE IF EXISTS previous_release");
            sql.execute("CREATE DATABASE previous_release");
        }
        Flyway.configure().dataSource(url("previous_release"), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("14").load().migrate();
        try (Connection old = connect("previous_release"); Statement sql = old.createStatement()) {
            sql.execute("insert into parties (party_code, name) values ('PTY-2026-0001', 'Old Supplier')");
            sql.execute("insert into financial_accounts (account_name, account_type) values ('Cash', 'CASH')");
            sql.execute("insert into financial_transactions (financial_account_id, transaction_date, transaction_type,"
                    + " direction, amount) select id, current_date, 'OPENING_BALANCE', 'IN', 5000.00 from financial_accounts");
            sql.execute("insert into material_movements (movement_number, product_type, to_product_type, movement_type,"
                    + " quantity_kg, to_location, movement_date) values ('MOV-2026-0001', 'RAW_SILK', 'RAW_SILK',"
                    + " 'OPENING_BALANCE', 12.500, 'RAW_STOCK', current_date)");
            sql.execute("insert into inventory_balances (product_type, location, quantity_kg)"
                    + " values ('RAW_SILK', 'RAW_STOCK', 12.500)");
        }

        // the current migrations apply on top of it and keep every row
        Flyway current = Flyway.configure()
                .dataSource(url("previous_release"), POSTGRES.getUsername(), POSTGRES.getPassword()).load();
        current.migrate();
        assertThat(current.info().pending()).isEmpty();
        try (Connection now = connect("previous_release"); Statement sql = now.createStatement()) {
            assertThat(count(sql, "select count(*) from parties")).isOne();
            assertThat(count(sql, "select count(*) from financial_transactions where amount = 5000.00")).isOne();
            assertThat(count(sql, "select count(*) from inventory_balances where quantity_kg = 12.500")).isOne();
            // and the new rules hold for the old rows too: a ledger row from before is now immutable (V16)
            assertThatThrownBy(() -> sql.executeUpdate("update financial_transactions set amount = 1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("never changed or deleted");
        }

        // AC2: a merged migration whose content changed is refused (checksum), so it can never be edited silently
        Path copy = Files.createTempDirectory("migrations");
        for (Resource migration : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
            Files.write(copy.resolve(migration.getFilename()), migration.getContentAsByteArray());
        }
        Path edited = copy.resolve("V5__create_identity_tables.sql");
        Files.writeString(edited, Files.readString(edited) + "\n-- edited after merge\n");
        assertThatThrownBy(() -> Flyway.configure()
                .dataSource(url("previous_release"), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:" + copy).load().validate())
                .isInstanceOf(FlywayValidateException.class).hasMessageContaining("checksum");
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(url(database), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String url(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }

    private static int count(Statement sql, String query) throws SQLException {
        try (ResultSet rs = sql.executeQuery(query)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private UUID insertParty() {
        return jdbc.queryForObject("insert into parties (party_code, name) values (?, 'SBI') returning id",
                UUID.class, "PTY-TEST-" + UUID.randomUUID().toString().substring(0, 8));
    }
}

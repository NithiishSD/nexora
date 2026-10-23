package com.nexora.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.nexora.support.AbstractApiTest;

// NFR-REL-05: the database connection dies in the middle of a request (what a database restart does to every open
// connection): the request fails cleanly (a problem body, nothing half-written, no idempotency key kept) and the
// client's retry with the same key then succeeds exactly once on a fresh connection.
class FailureDrillIntegrationTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-REL-05 database restart mid request fails cleanly and retry succeeds")
    void databaseRestartMidRequest_failsCleanlyAndRetrySucceeds() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "1000.00");
        String customer = data.customer().id().toString();
        String key = UUID.randomUUID().toString();
        String body = """
                {"purpose":"CUSTOMER","partyId":"%s","amount":250.00,"paymentMethod":"UPI","financialAccountId":"%s"}"""
                .formatted(customer, bank);

        MockHttpServletResponse failed;
        // another session holds the account row, so the payment waits inside its transaction...
        try (Connection holder = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "select id from financial_accounts where id = ?::uuid for update")) {
                lock.setString(1, bank);
                lock.executeQuery().close();
            }
            CompletableFuture<MockHttpServletResponse> request = CompletableFuture.supplyAsync(() -> {
                try {
                    return asOwner(postWithKey("/api/v1/payments", body, key)).andReturn().getResponse();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            // ...and its connection is killed while it waits (before the 5 s lock timeout)
            Integer waiting = null;
            for (int i = 0; i < 40 && waiting == null; i++) {
                Thread.sleep(50);
                waiting = jdbc.query("""
                        select pid from pg_stat_activity
                        where datname = current_database() and wait_event_type = 'Lock' and pid <> pg_backend_pid()""",
                        rs -> rs.next() ? rs.getInt(1) : null);
            }
            assertThat(waiting).as("the payment is waiting on the row lock").isNotNull();
            jdbc.queryForObject("select pg_terminate_backend(?)", Boolean.class, waiting);
            failed = request.get(20, TimeUnit.SECONDS);
            holder.rollback();
        }

        // failed cleanly: a problem body with a stable code and no internals, nothing written, the key not kept
        assertThat(failed.getStatus()).isGreaterThanOrEqualTo(500);
        assertThat(failed.getContentAsString()).contains("\"code\":\"INTERNAL_ERROR\"")
                .doesNotContain("Exception").doesNotContain("terminat").doesNotContain("select ");
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type ="
                + " 'CUSTOMER_PAYMENT'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from idempotency_keys", Integer.class)).isZero();

        // the retry (same key, as the client sends it) succeeds on a fresh connection, exactly once
        asOwner(postWithKey("/api/v1/payments", body, key)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(250.00));
        asOwner(postWithKey("/api/v1/payments", body, key)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isOne();
        asOwner(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(1250.00));
    }
}

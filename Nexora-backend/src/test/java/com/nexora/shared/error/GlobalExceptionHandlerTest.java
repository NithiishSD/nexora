package com.nexora.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

// Unit test with NO Spring context: this is the payoff of constructor injection.
// We create the handler ourselves and hand it a fixed (fake) clock.
class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("NFR-USE-04 handle business builds problem detail with code status and timestamp")
    void handleBusiness_buildsProblemDetailWithCodeStatusAndTimestamp() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/demo/error");
        BusinessException ex =
                new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Only 42.500 kg available.");

        ProblemDetail pd = handler.handleBusiness(ex, request);

        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getDetail()).isEqualTo("Only 42.500 kg available.");
        assertThat(pd.getInstance()).hasToString("/api/v1/demo/error");
        assertThat(pd.getProperties())
                .containsEntry("code", "INSUFFICIENT_INVENTORY")
                .containsEntry("timestamp", NOW);   // proves the injected clock is used
    }

    @Test
    @DisplayName("FR-INV-06 lock timeout maps to409 concurrent modification")
    void lockTimeout_mapsTo409ConcurrentModification() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/inventory/wastage");

        // what Spring raises when PostgreSQL's lock_timeout (or deadlock detection) aborts the statement
        ProblemDetail pd = handler.handlePessimisticLock(
                new CannotAcquireLockException("canceling statement due to lock timeout"), request);

        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getProperties()).containsEntry("code", "CONCURRENT_MODIFICATION");
        assertThat(pd.getDetail()).doesNotContain("lock timeout");   // no database text leaks to the client
    }

    @Test
    @DisplayName("NFR-USE-04 handle generic does not leak exception message")
    void handleGeneric_doesNotLeakExceptionMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");

        ProblemDetail pd = handler.handleGeneric(new RuntimeException("SELECT * FROM secret_table"), request);

        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("secret_table");
        assertThat(pd.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("NFR-DATA-06 unique violation on a business number is duplicate business number")
    void uniqueViolation_onBusinessNumber_isDuplicateBusinessNumber() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");

        ProblemDetail number = handler.handleDataIntegrity(new DataIntegrityViolationException("x", new SQLException(
                "duplicate key value violates unique constraint \"uq_payments_payment_number\"", "23505")), request);
        ProblemDetail code = handler.handleDataIntegrity(new DataIntegrityViolationException("x", new SQLException(
                "duplicate key value violates unique constraint \"uq_parties_party_code\"", "23505")), request);
        ProblemDetail other = handler.handleDataIntegrity(new DataIntegrityViolationException("x", new SQLException(
                "duplicate key value violates unique constraint \"uq_financial_accounts_account_name\"", "23505")),
                request);

        assertThat(number.getStatus()).isEqualTo(409);
        assertThat(number.getProperties()).containsEntry("code", "DUPLICATE_BUSINESS_NUMBER");
        assertThat(code.getProperties()).containsEntry("code", "DUPLICATE_BUSINESS_NUMBER");
        assertThat(other.getProperties()).containsEntry("code", "DUPLICATE_RESOURCE");
        assertThat(number.getDetail()).doesNotContain("uq_");
    }
}

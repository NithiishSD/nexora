package com.nexora.finance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.nexora.finance.api.dto.CreateAccountRequest;
import com.nexora.finance.api.dto.TransferRequest;
import com.nexora.finance.api.dto.TransferResponse;
import com.nexora.finance.domain.AccountType;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;
import com.nexora.support.DatabaseCleaner;

// FR-FIN-10: A -> B and B -> A at the same moment, 100 times. Both lock the two accounts in ascending id, so they queue
// instead of deadlocking. Mutation check (by hand, never committed): lock source then destination (request order) and
// PostgreSQL reports deadlocks here.
class TransferConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    TransferService transfers;

    @Autowired
    FinancialAccountService accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @BeforeEach
    void clean() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("FR-FIN-10 NFR-TEST-05 opposite transfers do not deadlock")
    void oppositeTransfers_doNotDeadlock() throws Exception {
        UUID a = account("Bank A", AccountType.BANK, "Canara");
        UUID b = account("Bank B", AccountType.BANK, "Indian Bank");

        for (int round = 0; round < 100; round++) {
            List<Outcome<TransferResponse>> outcomes = ConcurrentRunner.runTogether(List.of(
                    () -> transfers.transfer(request(a, b, "7.00")), () -> transfers.transfer(request(b, a, "3.00"))));
            assertThat(outcomes).as("round %d: %s", round, outcomes).allMatch(Outcome::succeeded);
        }

        // AC1: totals consistent: A lost 4.00 a round, B gained it; the money in the system is unchanged
        assertThat(accounts.get(a).balance()).isEqualByComparingTo("600.00");
        assertThat(accounts.get(b).balance()).isEqualByComparingTo("1400.00");
        assertThat(jdbc.queryForObject("select count(*) from account_transfers", Integer.class)).isEqualTo(200);
    }

    private UUID account(String name, AccountType type, String bank) {
        return accounts.create(new CreateAccountRequest(name, type, bank, null, new BigDecimal("1000.00"),
                LocalDate.now(clock))).id();
    }

    private TransferRequest request(UUID from, UUID to, String amount) {
        return new TransferRequest(from, to, new BigDecimal(amount), LocalDate.now(clock), null);
    }
}

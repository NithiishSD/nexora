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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.finance.api.dto.CreateAccountRequest;
import com.nexora.finance.domain.AccountType;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;
import com.nexora.support.DatabaseCleaner;

// Proves the account row lock (FR-FIN-06 AC4, NFR-TEST-05). Mutation check (by hand, never committed): without
// @Lock(PESSIMISTIC_WRITE) on FinancialAccountRepository.findByIdForUpdate both cash-outs pass the balance check.
class CashConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    FinanceService finance;

    @Autowired
    FinancialAccountService accounts;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @BeforeEach
    void clean() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("FR-FIN-06 NFR-TEST-05 parallel cash outs never drive balance negative")
    void parallelCashOuts_neverDriveBalanceNegative() throws Exception {
        for (int round = 0; round < 5; round++) {
            UUID cash = accounts.create(new CreateAccountRequest("Cash " + round, AccountType.CASH, null, null,
                    new BigDecimal("10000.00"), LocalDate.now(clock))).id();

            List<Outcome<TransactionResult>> outcomes = ConcurrentRunner.runTogether(List.of(
                    () -> cashOut(cash), () -> cashOut(cash)));

            // exactly one wins; the loser saw the reduced balance and got the business error
            assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
            assertThat(outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow().error())
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
            assertThat(accounts.get(cash).balance()).isEqualByComparingTo("3000.00");
        }
    }

    private TransactionResult cashOut(UUID account) {
        return new TransactionTemplate(transactionManager).execute(s -> finance.recordTransaction(
                new TransactionCommand(account, Direction.OUT, new BigDecimal("7000.00"), TransactionType.ADJUSTMENT,
                        LocalDate.now(clock), "cash out", null, TransactionReferenceType.NONE, null)));
    }
}

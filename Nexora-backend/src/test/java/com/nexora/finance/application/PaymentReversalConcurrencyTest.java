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
import com.nexora.finance.api.dto.PaymentResponse;
import com.nexora.finance.api.dto.RecordPaymentRequest;
import com.nexora.finance.domain.AccountType;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;
import com.nexora.support.DatabaseCleaner;
import com.nexora.support.TestDataFactory;

// Two people pressing "Reverse" on the same payment at the same moment (NFR-TEST-05). Mutation check (by hand, never
// committed): without the party and payment row locks the second reversal is not refused as INVALID_STATE_TRANSITION.
class PaymentReversalConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    PaymentService payments;

    @Autowired
    PaymentReversalService reversals;

    @Autowired
    FinancialAccountService accounts;

    @Autowired
    PartyService partyService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @BeforeEach
    void clean() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("FR-PAY-15 NFR-TEST-05 parallel reversals reverse once")
    void parallelReversals_reverseOnce() throws Exception {
        UUID customer = new TestDataFactory(partyService).customer().id();
        UUID cash = accounts.create(new CreateAccountRequest("Cash", AccountType.CASH, null, null, null, null)).id();
        for (int round = 0; round < 5; round++) {
            UUID payment = payments.record(new RecordPaymentRequest(PaymentPurpose.CUSTOMER, customer,
                    new BigDecimal("1000.00"), PaymentMethod.CASH, cash, LocalDate.now(clock), null, null, null)).id();

            List<Outcome<PaymentResponse>> outcomes = ConcurrentRunner.runTogether(List.of(
                    () -> reversals.reverse(payment, "first"), () -> reversals.reverse(payment, "second")));

            // exactly one reversal; the other sees REVERSED and is refused with the business error
            assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
            assertThat(outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow().error())
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
            assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type ="
                    + " 'REVERSAL' and payment_id = ?", Integer.class, payment)).isEqualTo(1);
        }
        // every payment in and every reversal out: the cash box is back at zero, never below
        assertThat(accounts.get(cash).balance()).isEqualByComparingTo("0.00");
    }
}

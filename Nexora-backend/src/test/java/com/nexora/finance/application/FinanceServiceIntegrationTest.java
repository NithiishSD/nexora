package com.nexora.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.finance.api.dto.CreateAccountRequest;
import com.nexora.finance.domain.AccountType;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.DatabaseCleaner;
import com.nexora.support.TestDataFactory;

// FinanceService against the real schema: the balance check under the account lock, the command rules, and the
// database CHECKs and trigger that back them up.
class FinanceServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    FinanceService finance;

    @Autowired
    FinancialAccountService accounts;

    @Autowired
    PartyService partyService;

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
    @DisplayName("FR-FIN-06 record transaction out above balance throws insufficient funds")
    void recordTransaction_outAboveBalance_throwsInsufficientFunds() {
        UUID cash = account("Cash", AccountType.CASH, "10000.00");

        // AC1: 10000 - 4000 = 6000
        record(adjustment(cash, Direction.OUT, "4000.00"));
        assertThat(balance(cash)).isEqualByComparingTo("6000.00");
        // AC2: more than the balance -> 409 INSUFFICIENT_FUNDS, nothing stored
        assertThatThrownBy(() -> record(adjustment(cash, Direction.OUT, "6000.01")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        assertThat(balance(cash)).isEqualByComparingTo("6000.00");
        // exactly the balance is allowed: the account may reach 0.00, never below
        record(adjustment(cash, Direction.OUT, "6000.00"));
        assertThat(balance(cash)).isEqualByComparingTo("0.00");

        // AC3: a bank account cannot go negative either (no overdraft, Q4)
        UUID bank = account("SBI Current", AccountType.BANK, "5000.00");
        assertThatThrownBy(() -> record(adjustment(bank, Direction.OUT, "6000.00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));

        // database backstop: an OUT row written past the service is refused by the trigger
        assertThatThrownBy(() -> jdbc.update("insert into financial_transactions (financial_account_id,"
                + " transaction_date, transaction_type, direction, amount, description) values (?, ?, 'ADJUSTMENT',"
                + " 'OUT', 5000.01, 'bypass')", bank, LocalDate.now(clock)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("would go negative");
        assertThat(balance(bank)).isEqualByComparingTo("5000.00");

        // a closed account takes nothing (409); an unknown one is 404
        jdbc.update("update financial_accounts set status = 'CLOSED' where id = ?", cash);
        assertThatThrownBy(() -> record(adjustment(cash, Direction.IN, "1.00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThatThrownBy(() -> record(adjustment(UUID.randomUUID(), Direction.IN, "1.00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("FR-FIN-05 transaction requires exactly one reference or none")
    void transaction_requiresExactlyOneReferenceOrNone() {
        UUID cash = account("Cash", AccountType.CASH, "1000.00");
        Map<TransactionReferenceType, UUID> refs = references(cash);

        // the service refuses a reference where none belongs, and a missing or wrong one where it does
        assertThatThrownBy(() -> record(new TransactionCommand(cash, Direction.IN, new BigDecimal("1.00"),
                TransactionType.OPENING_BALANCE, today(), null, null, TransactionReferenceType.PAYMENT,
                refs.get(TransactionReferenceType.PAYMENT)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record(new TransactionCommand(cash, Direction.OUT, new BigDecimal("1.00"),
                TransactionType.EXPENSE, today(), null, null, TransactionReferenceType.NONE, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record(new TransactionCommand(cash, Direction.OUT, new BigDecimal("1.00"),
                TransactionType.EXPENSE, today(), null, null, TransactionReferenceType.LOAN,
                refs.get(TransactionReferenceType.LOAN)))).isInstanceOf(IllegalArgumentException.class);
        // AC1 shape: the one reference is stored in its own column and read back as the reference type
        record(new TransactionCommand(cash, Direction.OUT, new BigDecimal("1.00"), TransactionType.EXPENSE, today(),
                "diesel", "EXP-2026-0001", TransactionReferenceType.EXPENSE, refs.get(TransactionReferenceType.EXPENSE)));
        assertThat(jdbc.queryForObject("select expense_id from financial_transactions where transaction_type = 'EXPENSE'",
                UUID.class)).isEqualTo(refs.get(TransactionReferenceType.EXPENSE));

        // AC2: two references on one row are refused by the database
        assertThatThrownBy(() -> jdbc.update("insert into financial_transactions (financial_account_id,"
                + " transaction_date, transaction_type, direction, amount, payment_id, expense_id) values"
                + " (?, ?, 'CUSTOMER_PAYMENT', 'IN', 1.00, ?, ?)", cash, today(),
                refs.get(TransactionReferenceType.PAYMENT), refs.get(TransactionReferenceType.EXPENSE)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_financial_transactions_single_reference");
    }

    @Test
    @DisplayName("FR-FIN-05 FR-FIN-15 FR-CHIT-04 type direction reference rules match type table")
    void typeDirectionReferenceRules_matchTypeTable() {
        UUID cash = account("Cash", AccountType.CASH, "1000000.00");
        UUID bank = account("SBI Current", AccountType.BANK, "1000000.00");
        Map<TransactionReferenceType, UUID> refs = references(cash);

        for (TransactionType type : TransactionType.values()) {
            if (type == TransactionType.REVERSAL) {
                continue;                                       // written only by the reversal use cases
            }
            Direction direction = type.direction() == null ? Direction.OUT : type.direction();
            UUID refId = refs.get(type.reference());
            String description = type == TransactionType.ADJUSTMENT ? "recount" : null;
            // the service accepts the documented direction and reference ...
            record(new TransactionCommand(cash, direction, new BigDecimal("1.00"), type, today(), description, null,
                    type.reference(), refId));
            if (type.direction() != null) {
                // ... and refuses the other direction; so does the database
                assertThatThrownBy(() -> record(new TransactionCommand(cash, direction.opposite(),
                        new BigDecimal("1.00"), type, today(), description, null, type.reference(), refId)))
                        .as(type.name()).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> insert(cash, type, direction.opposite(), type.reference(), refId))
                        .as(type.name()).isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("ck_financial_transactions_type_direction");
            }
            if (type.reference() != TransactionReferenceType.NONE) {
                // a type that needs a reference is refused by the database without one
                assertThatThrownBy(() -> insert(cash, type, direction, TransactionReferenceType.NONE, null))
                        .as(type.name()).isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("ck_financial_transactions_type_reference");
            }
        }
        // FR-FIN-15: an adjustment goes either way but needs its reason
        record(adjustment(cash, Direction.IN, "1.00"));
        assertThatThrownBy(() -> record(new TransactionCommand(cash, Direction.IN, new BigDecimal("1.00"),
                TransactionType.ADJUSTMENT, today(), " ", null, TransactionReferenceType.NONE, null)))
                .isInstanceOf(IllegalArgumentException.class);
        // FR-CHIT-04 AC2: chit money only through a cash account (422 INVALID_PAYMENT_METHOD)
        assertThatThrownBy(() -> record(new TransactionCommand(bank, Direction.OUT, new BigDecimal("2000.00"),
                TransactionType.CHIT_ORGANISER_PAYMENT, today(), null, null, TransactionReferenceType.CHIT,
                refs.get(TransactionReferenceType.CHIT))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_METHOD));
        // REVERSAL needs the row it reverses, so it never goes through recordTransaction
        assertThatThrownBy(() -> record(new TransactionCommand(cash, Direction.IN, new BigDecimal("1.00"),
                TransactionType.REVERSAL, today(), null, null, TransactionReferenceType.NONE, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- helpers --------------------------------------------------------------------------

    private UUID account(String name, AccountType type, String opening) {
        return accounts.create(new CreateAccountRequest(name, type, type == AccountType.BANK ? "State Bank" : null,
                null, new BigDecimal(opening), today())).id();
    }

    // One row of every business record a transaction can point at (minimal valid columns).
    private Map<TransactionReferenceType, UUID> references(UUID account) {
        UUID other = accounts.create(new CreateAccountRequest("Second cash", AccountType.CASH, null, null, null, null)).id();
        UUID party = new TestDataFactory(partyService).customer().id();
        Map<TransactionReferenceType, UUID> refs = new EnumMap<>(TransactionReferenceType.class);
        refs.put(TransactionReferenceType.PAYMENT, jdbc.queryForObject("insert into payments (payment_number, party_id,"
                + " purpose, direction, amount, payment_method, financial_account_id, payment_date) values"
                + " ('PAY-T-1', ?, 'CUSTOMER', 'RECEIVED', 1.00, 'CASH', ?, ?) returning id", UUID.class,
                party, account, today()));
        refs.put(TransactionReferenceType.EXPENSE, jdbc.queryForObject("insert into expenses (expense_number, category,"
                + " amount, expense_date, financial_account_id, payment_method) values ('EXP-T-1', 'OTHER', 1.00, ?, ?,"
                + " 'CASH') returning id", UUID.class, today(), account));
        refs.put(TransactionReferenceType.LOAN, jdbc.queryForObject("insert into loans (loan_number, party_id, loan_type,"
                + " principal_amount, start_date) values ('LON-T-1', ?, 'PRIVATE', 1.00, ?) returning id", UUID.class,
                party, today()));
        refs.put(TransactionReferenceType.CHIT, jdbc.queryForObject("insert into chits (chit_number, chit_name,"
                + " total_amount, monthly_amount, duration_months, start_date) values ('CHT-T-1', 'Test chit', 12.00,"
                + " 1.00, 12, ?) returning id", UUID.class, today()));
        refs.put(TransactionReferenceType.TRANSFER, jdbc.queryForObject("insert into account_transfers (transfer_number,"
                + " source_account_id, destination_account_id, amount, transfer_date) values ('TRF-T-1', ?, ?, 1.00, ?)"
                + " returning id", UUID.class, account, other, today()));
        return refs;
    }

    private void insert(UUID account, TransactionType type, Direction direction, TransactionReferenceType ref, UUID id) {
        String column = switch (ref) {
            case PAYMENT -> "payment_id";
            case EXPENSE -> "expense_id";
            case LOAN -> "loan_id";
            case CHIT -> "chit_id";
            case TRANSFER -> "account_transfer_id";
            case NONE -> null;
        };
        if (column == null) {
            jdbc.update("insert into financial_transactions (financial_account_id, transaction_date, transaction_type,"
                    + " direction, amount, description) values (?, ?, ?, ?, 1.00, 'x')", account, today(), type.name(),
                    direction.name());
        } else {
            jdbc.update("insert into financial_transactions (financial_account_id, transaction_date, transaction_type,"
                    + " direction, amount, " + column + ") values (?, ?, ?, ?, 1.00, ?)", account, today(), type.name(),
                    direction.name(), id);
        }
    }

    private TransactionCommand adjustment(UUID account, Direction direction, String amount) {
        return new TransactionCommand(account, direction, new BigDecimal(amount), TransactionType.ADJUSTMENT, today(),
                "physical count", null, TransactionReferenceType.NONE, null);
    }

    private TransactionResult record(TransactionCommand command) {
        return new TransactionTemplate(transactionManager).execute(s -> finance.recordTransaction(command));
    }

    private BigDecimal balance(UUID account) {
        return accounts.get(account).balance();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}

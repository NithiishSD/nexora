package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.shared.domain.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// One row of the money ledger (DOMAIN_RULES rule 6). Append-only: every column is updatable = false and nothing
// changes after creation; a mistake is corrected by a REVERSAL row. Created only by FinanceService.
@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction extends AuditableEntity {

    @Column(name = "financial_account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "transaction_date", nullable = false, updatable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30, updatable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private Direction direction;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    // At most one business reference (ck_financial_transactions_single_reference).
    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "expense_id", updatable = false)
    private UUID expenseId;

    @Column(name = "loan_id", updatable = false)
    private UUID loanId;

    @Column(name = "chit_id", updatable = false)
    private UUID chitId;

    @Column(name = "account_transfer_id", updatable = false)
    private UUID accountTransferId;

    @Column(name = "reverses_transaction_id", updatable = false)
    private UUID reversesTransactionId;

    @Column(name = "reference_number", length = 100, updatable = false)
    private String referenceNumber;

    @Column(columnDefinition = "text", updatable = false)
    private String description;

    protected FinancialTransaction() {
        // for JPA only
    }

    // Factory used only by FinanceService, after it has checked the rules and the balance.
    public static FinancialTransaction record(UUID accountId, LocalDate date, TransactionType type, Direction direction,
                                              BigDecimal amount, TransactionReferenceType referenceType, UUID referenceId,
                                              UUID reversesTransactionId, String referenceNumber, String description) {
        FinancialTransaction t = new FinancialTransaction();
        t.accountId = accountId;
        t.transactionDate = date;
        t.transactionType = type;
        t.direction = direction;
        t.amount = amount;
        switch (referenceType) {
            case PAYMENT -> t.paymentId = referenceId;
            case EXPENSE -> t.expenseId = referenceId;
            case LOAN -> t.loanId = referenceId;
            case CHIT -> t.chitId = referenceId;
            case TRANSFER -> t.accountTransferId = referenceId;
            case NONE -> { }
        }
        t.reversesTransactionId = reversesTransactionId;
        t.referenceNumber = referenceNumber;
        t.description = description;
        return t;
    }

    // Which business record the row belongs to (derived from the one column that is set).
    public TransactionReferenceType getReferenceType() {
        if (paymentId != null) {
            return TransactionReferenceType.PAYMENT;
        }
        if (expenseId != null) {
            return TransactionReferenceType.EXPENSE;
        }
        if (loanId != null) {
            return TransactionReferenceType.LOAN;
        }
        if (chitId != null) {
            return TransactionReferenceType.CHIT;
        }
        return accountTransferId != null ? TransactionReferenceType.TRANSFER : TransactionReferenceType.NONE;
    }

    public UUID getReferenceId() {
        return switch (getReferenceType()) {
            case PAYMENT -> paymentId;
            case EXPENSE -> expenseId;
            case LOAN -> loanId;
            case CHIT -> chitId;
            case TRANSFER -> accountTransferId;
            case NONE -> null;
        };
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getReversesTransactionId() {
        return reversesTransactionId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getDescription() {
        return description;
    }
}

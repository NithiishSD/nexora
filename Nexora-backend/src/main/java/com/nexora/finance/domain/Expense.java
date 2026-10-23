package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.error.InvalidStateTransitionException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// Money spent (FR-FIN-11): one OUT ledger row (EXPENSE, or PERSONAL_DRAWING for the owner's own use). Never edited; a
// mistake is reversed, which adds a REVERSAL row and keeps the expense visible.
@Entity
@Table(name = "expenses")
public class Expense extends AuditableEntity {

    @Column(name = "expense_number", nullable = false, length = 30, updatable = false)
    private String expenseNumber;

    @Column(nullable = false, length = 30, updatable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_kind", nullable = false, length = 10, updatable = false)
    private ExpenseKind expenseKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Recurrence recurrence;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false, updatable = false)
    private LocalDate expenseDate;

    @Column(name = "financial_account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 15, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(columnDefinition = "text", updatable = false)
    private String description;

    @Column(name = "document_reference", length = 100, updatable = false)
    private String documentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecordStatus status;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    protected Expense() {
        // for JPA only
    }

    public static Expense record(String number, String category, ExpenseKind kind, Recurrence recurrence,
                                 BigDecimal amount, LocalDate date, UUID accountId, PaymentMethod method,
                                 String description, String documentReference) {
        Expense e = new Expense();
        e.expenseNumber = number;
        e.category = category;
        e.expenseKind = kind;
        e.recurrence = recurrence;
        e.amount = amount;
        e.expenseDate = date;
        e.accountId = accountId;
        e.paymentMethod = method;
        e.description = description;
        e.documentReference = documentReference;
        e.status = RecordStatus.RECORDED;
        return e;
    }

    // RECORDED -> REVERSED, once (FR-FIN-14).
    public void reverse(String reason, Instant at) {
        if (status != RecordStatus.RECORDED) {
            throw new InvalidStateTransitionException("Expense " + expenseNumber, status, RecordStatus.REVERSED);
        }
        status = RecordStatus.REVERSED;
        reversedAt = at;
        reversalReason = reason;
    }

    public String getExpenseNumber() {
        return expenseNumber;
    }

    public String getCategory() {
        return category;
    }

    public ExpenseKind getExpenseKind() {
        return expenseKind;
    }

    public Recurrence getRecurrence() {
        return recurrence;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getDescription() {
        return description;
    }

    public String getDocumentReference() {
        return documentReference;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public String getReversalReason() {
        return reversalReason;
    }
}

package com.nexora.loans.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.InvalidStateTransitionException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// Money borrowed from a LENDER (FR-LOAN-01/02): a liability, not revenue. Its received, repaid and interest amounts
// live in the money ledger (rows linked by loan_id); this entity holds the terms, the part repaid before go-live
// and the status. Outstanding principal is always derived.
@Entity
@Table(name = "loans")
public class Loan extends AuditableEntity {

    @Column(name = "loan_number", nullable = false, length = 30, updatable = false)
    private String loanNumber;

    @Column(name = "party_id", nullable = false, updatable = false)
    private UUID lenderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 10, updatable = false)
    private LoanType loanType;

    @Column(name = "principal_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal principalAmount;

    @Column(name = "repaid_before_go_live", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal repaidBeforeGoLive;

    @Column(name = "interest_notes", length = 500, updatable = false)
    private String interestNotes;

    @Column(name = "interest_rate_percent", precision = 6, scale = 3, updatable = false)
    private BigDecimal interestRatePercent;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "due_date", updatable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LoanStatus status;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    protected Loan() {
        // for JPA only
    }

    // outstandingAtGoLive: null for a loan received in the system (nothing repaid before), else what was still owed
    // when the system started (FR-LOAN-02): it may not exceed the principal (400).
    public static Loan register(String number, UUID lenderId, LoanType type, BigDecimal principal, LocalDate startDate,
                                LocalDate dueDate, BigDecimal interestRatePercent, String interestNotes, String notes,
                                BigDecimal outstandingAtGoLive) {
        if (dueDate != null && dueDate.isBefore(startDate)) {
            throw new FieldValidationException("dueDate", "The due date cannot be before the start date.");
        }
        BigDecimal p = Money.round(principal);
        if (outstandingAtGoLive != null && Money.round(outstandingAtGoLive).compareTo(p) > 0) {
            throw new FieldValidationException("outstandingPrincipal", "More cannot be outstanding than was borrowed ("
                    + p.toPlainString() + ").");
        }
        Loan loan = new Loan();
        loan.loanNumber = number;
        loan.lenderId = lenderId;
        loan.loanType = type;
        loan.principalAmount = p;
        loan.repaidBeforeGoLive = outstandingAtGoLive == null ? Money.zero()
                : p.subtract(Money.round(outstandingAtGoLive));
        loan.startDate = startDate;
        loan.dueDate = dueDate;
        loan.interestRatePercent = interestRatePercent;
        loan.interestNotes = interestNotes;
        loan.notes = notes;
        loan.status = LoanStatus.ACTIVE;
        return loan;
    }

    // principal - repaid before go-live - repaid since (net of reversals, from the ledger).
    public BigDecimal outstanding(BigDecimal principalRepaid) {
        return principalAmount.subtract(repaidBeforeGoLive).subtract(principalRepaid);
    }

    // FR-LOAN-03: a repayment may not exceed what is still owed; nothing is paid on a closed loan.
    public void requireRepayable(BigDecimal amount, BigDecimal principalRepaid) {
        requireActive();
        BigDecimal outstanding = outstanding(principalRepaid);
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ALLOCATION_EXCEEDED, "Loan " + loanNumber + " has "
                    + outstanding.toPlainString() + " principal outstanding; " + amount.toPlainString()
                    + " is more than that.");
        }
    }

    public void requireActive() {
        if (status != LoanStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Loan " + loanNumber + " is closed.");
        }
    }

    // ACTIVE -> CLOSED only at 0.00 principal outstanding (FR-LOAN-06).
    public void close(BigDecimal principalRepaid) {
        if (status != LoanStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Loan " + loanNumber, status, LoanStatus.CLOSED);
        }
        BigDecimal outstanding = outstanding(principalRepaid);
        if (outstanding.signum() != 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Loan " + loanNumber + " still has "
                    + outstanding.toPlainString() + " principal outstanding.");
        }
        status = LoanStatus.CLOSED;
    }

    public String getLoanNumber() {
        return loanNumber;
    }

    public UUID getLenderId() {
        return lenderId;
    }

    public LoanType getLoanType() {
        return loanType;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public BigDecimal getRepaidBeforeGoLive() {
        return repaidBeforeGoLive;
    }

    public String getInterestNotes() {
        return interestNotes;
    }

    public BigDecimal getInterestRatePercent() {
        return interestRatePercent;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}

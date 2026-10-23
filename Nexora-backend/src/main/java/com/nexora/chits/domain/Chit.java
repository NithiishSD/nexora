package com.nexora.chits.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

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

// A chit-fund commitment, paid in physical cash only (FR-CHIT-01..09). Contributions, the payout and organiser payments
// are ledger rows linked by chit_id; this entity holds the terms, what was paid before go-live and the status. Totals
// are always derived. Amounts are entered by the owner: no bidding calculation (Q6).
@Entity
@Table(name = "chits")
public class Chit extends AuditableEntity {

    @Column(name = "chit_number", nullable = false, length = 30, updatable = false)
    private String chitNumber;

    @Column(name = "chit_name", nullable = false, length = 150, updatable = false)
    private String chitName;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "monthly_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal monthlyAmount;

    @Column(name = "duration_months", nullable = false, updatable = false)
    private int durationMonths;

    @Column(name = "prior_contributions_count", nullable = false, updatable = false)
    private int priorContributionsCount;

    @Column(name = "prior_contributions_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal priorContributionsAmount;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChitStatus status;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    protected Chit() {
        // for JPA only
    }

    // FR-CHIT-08: contributions paid before go-live are counted, never moved as cash; their count may not pass the
    // duration, and an amount needs a count (and the other way round).
    public static Chit create(String number, String name, BigDecimal total, BigDecimal monthly, int durationMonths,
                              LocalDate startDate, Integer priorCount, BigDecimal priorAmount, String notes) {
        int count = priorCount == null ? 0 : priorCount;
        if (count > durationMonths) {
            throw new FieldValidationException("priorContributionsCount", "More contributions than the "
                    + durationMonths + " months of the chit.");
        }
        if (count > 0 && priorAmount == null) {
            throw new FieldValidationException("priorContributionsAmount", "Enter the amount of the "
                    + count + " contributions paid before.");
        }
        if (count == 0 && priorAmount != null && priorAmount.signum() > 0) {
            throw new FieldValidationException("priorContributionsCount", "Enter how many contributions the amount"
                    + " paid before covers.");
        }
        Chit c = new Chit();
        c.chitNumber = number;
        c.chitName = name;
        c.totalAmount = Money.round(total);
        c.monthlyAmount = Money.round(monthly);
        c.durationMonths = durationMonths;
        c.priorContributionsCount = count;
        c.priorContributionsAmount = priorAmount == null ? Money.zero() : Money.round(priorAmount);
        c.startDate = startDate;
        c.notes = notes;
        c.status = ChitStatus.ACTIVE;
        return c;
    }

    // Contributions and organiser payments: while ACTIVE or MATURED (FR-CHIT-02 AC4, FR-CHIT-03 AC1).
    public void requireOpen() {
        if (status == ChitStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Chit " + chitNumber + " is closed.");
        }
    }

    // ACTIVE -> MATURED with the payout; a second payout is refused (FR-CHIT-03 AC2).
    public void receivePayout() {
        if (status != ChitStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Chit " + chitNumber, status, ChitStatus.MATURED);
        }
        status = ChitStatus.MATURED;
    }

    // FR-CHIT-09: reversing the payout takes a MATURED chit back to ACTIVE; a CLOSED chit stays CLOSED.
    public void payoutReversed() {
        if (status == ChitStatus.MATURED) {
            status = ChitStatus.ACTIVE;
        }
    }

    // ACTIVE or MATURED -> CLOSED (FR-CHIT-06).
    public void close() {
        if (status == ChitStatus.CLOSED) {
            throw new InvalidStateTransitionException("Chit " + chitNumber, status, ChitStatus.CLOSED);
        }
        status = ChitStatus.CLOSED;
    }

    public String getChitNumber() {
        return chitNumber;
    }

    public String getChitName() {
        return chitName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    public int getDurationMonths() {
        return durationMonths;
    }

    public int getPriorContributionsCount() {
        return priorContributionsCount;
    }

    public BigDecimal getPriorContributionsAmount() {
        return priorContributionsAmount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public ChitStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}

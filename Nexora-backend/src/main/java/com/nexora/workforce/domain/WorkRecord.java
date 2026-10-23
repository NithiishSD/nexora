package com.nexora.workforce.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.RecordStatus;
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

// Work done by a worker (the aggregate root of workforce): the measure that fits the work type, the rate in force
// when it was recorded and the frozen amount (FR-WORK-01..03). Nothing here is ever edited; a mistake is reversed.
@Entity
@Table(name = "work_records")
public class WorkRecord extends AuditableEntity {

    @Column(name = "worker_id", nullable = false, updatable = false)
    private UUID workerId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 10, updatable = false)
    private WorkType workType;

    @Column(precision = 8, scale = 2, updatable = false)
    private BigDecimal hours;

    @Column(name = "quantity_kg", precision = 12, scale = 3, updatable = false)
    private BigDecimal quantityKg;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal rate;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "production_batch_id", updatable = false)
    private UUID productionBatchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecordStatus status;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    protected WorkRecord() {
        // for JPA only
    }

    // FR-WORK-02: rolling carries hours only, warping kg only (400 on the field that is missing or not allowed).
    // amount = round(units x rate, 2, HALF_UP), computed here and never accepted from the client.
    public static WorkRecord record(UUID workerId, LocalDate workDate, WorkType workType, BigDecimal hours,
                                    BigDecimal quantityKg, BigDecimal rate, UUID productionBatchId, String notes) {
        BigDecimal units = switch (workType) {
            case ROLLING -> requireOnly("hours", hours, "quantityKg", quantityKg, "Rolling work is paid by the hour");
            case WARPING -> requireOnly("quantityKg", quantityKg, "hours", hours, "Warping work is paid by the kg");
        };
        WorkRecord w = new WorkRecord();
        w.workerId = workerId;
        w.workDate = workDate;
        w.workType = workType;
        w.hours = hours;
        w.quantityKg = quantityKg;
        w.rate = Money.round(rate);
        w.amount = Money.round(units.multiply(w.rate));
        w.productionBatchId = productionBatchId;
        w.status = RecordStatus.RECORDED;
        w.notes = notes;
        return w;
    }

    // RECORDED -> REVERSED (FR-WORK-06), only while no active payment is allocated to it: pay-back is a payment
    // reversal first. The amounts stay; the record just stops counting.
    public void reverse(String reason, boolean hasAllocations, Instant at) {
        if (status != RecordStatus.RECORDED) {
            throw new InvalidStateTransitionException("Work record", status, RecordStatus.REVERSED);
        }
        if (hasAllocations) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "This work record has payments allocated"
                    + " to it; reverse the payment first.");
        }
        status = RecordStatus.REVERSED;
        reversedAt = at;
        reversalReason = reason;
    }

    private static BigDecimal requireOnly(String field, BigDecimal value, String other, BigDecimal otherValue,
                                          String why) {
        if (value == null || value.signum() <= 0) {
            throw new FieldValidationException(field, why + "; " + field + " must be greater than 0.");
        }
        if (otherValue != null) {
            throw new FieldValidationException(other, why + "; leave " + other + " out.");
        }
        return value;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public WorkType getWorkType() {
        return workType;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getProductionBatchId() {
        return productionBatchId;
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

    public String getNotes() {
        return notes;
    }
}

package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.nexora.shared.domain.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// How much of one worker payment went to one work record or opening payable. Append-only.
@Entity
@Table(name = "worker_payment_allocations")
public class WorkerPaymentAllocation extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(name = "work_record_id", updatable = false)
    private UUID workRecordId;

    @Column(name = "opening_obligation_id", updatable = false)
    private UUID openingObligationId;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal allocatedAmount;

    protected WorkerPaymentAllocation() {
        // for JPA only
    }

    static WorkerPaymentAllocation of(Payment payment, PlannedAllocation a) {
        WorkerPaymentAllocation row = new WorkerPaymentAllocation();
        row.payment = payment;
        switch (a.targetType()) {
            case WORK_RECORD -> row.workRecordId = a.targetId();
            case OPENING_OBLIGATION -> row.openingObligationId = a.targetId();
            default -> throw new IllegalArgumentException("A worker payment cannot pay a " + a.targetType());
        }
        row.allocatedAmount = a.amount();
        return row;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }
}

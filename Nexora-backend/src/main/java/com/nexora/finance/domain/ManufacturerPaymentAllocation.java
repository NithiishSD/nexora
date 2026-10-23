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

// How much of one manufacturer payment went to one outsourcing job or opening payable. Append-only.
@Entity
@Table(name = "manufacturer_payment_allocations")
public class ManufacturerPaymentAllocation extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(name = "outsourcing_job_id", updatable = false)
    private UUID outsourcingJobId;

    @Column(name = "opening_obligation_id", updatable = false)
    private UUID openingObligationId;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal allocatedAmount;

    protected ManufacturerPaymentAllocation() {
        // for JPA only
    }

    static ManufacturerPaymentAllocation of(Payment payment, PlannedAllocation a) {
        ManufacturerPaymentAllocation row = new ManufacturerPaymentAllocation();
        row.payment = payment;
        switch (a.targetType()) {
            case OUTSOURCING_JOB -> row.outsourcingJobId = a.targetId();
            case OPENING_OBLIGATION -> row.openingObligationId = a.targetId();
            default -> throw new IllegalArgumentException("A manufacturer payment cannot pay a " + a.targetType());
        }
        row.allocatedAmount = a.amount();
        return row;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }
}

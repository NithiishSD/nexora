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

// How much of one supplier payment went to one purchase or opening payable. Append-only, like the customer rows.
@Entity
@Table(name = "supplier_payment_allocations")
public class SupplierPaymentAllocation extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(name = "purchase_id", updatable = false)
    private UUID purchaseId;

    @Column(name = "opening_obligation_id", updatable = false)
    private UUID openingObligationId;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal allocatedAmount;

    protected SupplierPaymentAllocation() {
        // for JPA only
    }

    static SupplierPaymentAllocation of(Payment payment, PlannedAllocation a) {
        SupplierPaymentAllocation row = new SupplierPaymentAllocation();
        row.payment = payment;
        switch (a.targetType()) {
            case PURCHASE -> row.purchaseId = a.targetId();
            case OPENING_OBLIGATION -> row.openingObligationId = a.targetId();
            default -> throw new IllegalArgumentException("A supplier payment cannot pay a " + a.targetType());
        }
        row.allocatedAmount = a.amount();
        return row;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }
}

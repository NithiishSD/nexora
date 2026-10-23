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

// How much of one customer payment went to one order item or opening receivable. Append-only: a reversed
// payment's rows stay and are ignored by every outstanding query (they count only RECORDED payments).
@Entity
@Table(name = "customer_payment_allocations")
public class CustomerPaymentAllocation extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    // Another module's record: by id only. Exactly one of the two is set (ck_..._target).
    @Column(name = "order_item_id", updatable = false)
    private UUID orderItemId;

    @Column(name = "opening_obligation_id", updatable = false)
    private UUID openingObligationId;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal allocatedAmount;

    protected CustomerPaymentAllocation() {
        // for JPA only
    }

    static CustomerPaymentAllocation of(Payment payment, PlannedAllocation a) {
        CustomerPaymentAllocation row = new CustomerPaymentAllocation();
        row.payment = payment;
        switch (a.targetType()) {
            case ORDER_ITEM -> row.orderItemId = a.targetId();
            case OPENING_OBLIGATION -> row.openingObligationId = a.targetId();
            default -> throw new IllegalArgumentException("A customer payment cannot pay a " + a.targetType());
        }
        row.allocatedAmount = a.amount();
        return row;
    }

    public AllocationTargetType getTargetType() {
        return orderItemId != null ? AllocationTargetType.ORDER_ITEM : AllocationTargetType.OPENING_OBLIGATION;
    }

    public UUID getTargetId() {
        return orderItemId != null ? orderItemId : openingObligationId;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }
}

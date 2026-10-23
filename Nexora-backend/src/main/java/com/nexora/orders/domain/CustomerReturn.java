package com.nexora.orders.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Weight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Material sent back by a customer for an order item (FR-ORD-20, FR-INV-17). An append-only record:
// the original order and delivery keep their figures. Its stock effect is the CUSTOMER_RETURN movement that points here.
@Entity
@Table(name = "customer_returns")
public class CustomerReturn extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, updatable = false)
    private OrderItem orderItem;

    @Column(name = "return_number", nullable = false, length = 30, updatable = false)
    private String returnNumber;

    @Column(name = "return_date", nullable = false, updatable = false)
    private LocalDate returnDate;

    @Column(name = "returned_weight_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal returnedWeightKg;

    @Column(nullable = false, updatable = false)
    private boolean usable;

    @Column(nullable = false, length = 255, updatable = false)
    private String reason;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    protected CustomerReturn() {
        // for JPA only
    }

    public static CustomerReturn of(CustomerOrder order, OrderItem orderItem, String returnNumber,
                                    LocalDate returnDate, BigDecimal returnedKg, boolean usable,
                                    String reason, String notes) {
        Objects.requireNonNull(order, "order is required");
        Objects.requireNonNull(orderItem, "orderItem is required");
        Objects.requireNonNull(returnNumber, "returnNumber is required");
        Objects.requireNonNull(returnDate, "returnDate is required");
        Objects.requireNonNull(reason, "reason is required");

        CustomerReturn r = new CustomerReturn();
        r.order = order;
        r.orderItem = orderItem;
        r.returnNumber = returnNumber;
        r.returnDate = returnDate;
        r.returnedWeightKg = Weight.round(returnedKg);
        r.usable = usable;
        r.reason = reason.trim();
        r.notes = notes == null || notes.isBlank() ? null : notes.trim();
        return r;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public BigDecimal getReturnedWeightKg() {
        return returnedWeightKg;
    }

    public boolean isUsable() {
        return usable;
    }

    public String getReason() {
        return reason;
    }

    public String getNotes() {
        return notes;
    }
}

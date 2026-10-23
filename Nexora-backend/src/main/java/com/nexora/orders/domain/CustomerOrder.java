package com.nexora.orders.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.InvalidStateTransitionException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

// A customer's order: header plus its lines (the aggregate root of the orders module). Every status change is a
// named method that checks the state machine of DOMAIN_RULES section 5; anything else is 409 INVALID_STATE_TRANSITION.
// Payment status, outstanding and "locked" are derived and never stored (04-DATA-MODEL decision 5).
@Entity
@Table(name = "customer_orders")
public class CustomerOrder extends AuditableEntity {

    @Column(name = "order_number", nullable = false, length = 30, updatable = false)
    private String orderNumber;

    // Another module's record: by id only (CONVENTIONS section 1).
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "order_date", nullable = false, updatable = false)
    private LocalDate orderDate;

    @Column(name = "required_date")
    private LocalDate requiredDate;

    @Column(name = "delivered_on")
    private LocalDate deliveredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(columnDefinition = "text")
    private String notes;

    // The order owns its lines (CONVENTIONS section 3): new lines are inserted with it, and a line dropped by an
    // edit before the order is locked is deleted (orphanRemoval). Lines are edited in place, so the
    // (order_id, line_no) unique key never sees two rows with the same number during a flush.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo asc")
    @BatchSize(size = 100)
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
        // for JPA only
    }

    // Factory: a new order is PLACED (not yet an obligation, assumption Q2) with at least one line.
    public static CustomerOrder place(String orderNumber, UUID customerId, LocalDate orderDate, LocalDate requiredDate,
                                      String notes, List<OrderLine> lines) {
        CustomerOrder order = new CustomerOrder();
        order.orderNumber = orderNumber;
        order.customerId = customerId;
        order.orderDate = orderDate;
        order.status = OrderStatus.PLACED;
        order.notes = notes;
        order.setRequiredDate(requiredDate);
        order.replaceLines(lines);
        return order;
    }

    // PATCH before the order is locked (FR-ORD-06). The service passes the current values for fields the request
    // left out. Rates and amounts change only here, and only while nothing has been paid against the order.
    public void edit(LocalDate requiredDate, String notes, List<OrderLine> lines, boolean hasAllocations) {
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Order " + orderNumber, status, "EDITED");
        }
        requireModifiable(hasAllocations);
        setRequiredDate(requiredDate);
        this.notes = notes;
        if (lines != null) {
            replaceLines(lines);
        }
    }

    // PLACED -> CONFIRMED: from now on the items are owed (assumption Q2, FR-ORD-07).
    public void confirm() {
        transition(OrderStatus.PLACED, OrderStatus.CONFIRMED);
    }

    // CONFIRMED -> IN_PROGRESS when production or outsourcing starts; the order is locked from here (FR-ORD-08).
    public void startWork() {
        transition(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS);
        items.forEach(i -> i.moveTo(OrderItemStatus.IN_PROGRESS));
    }

    // Production or outsourcing starts for this order (FR-ORD-08): a confirmed order becomes IN_PROGRESS and locked;
    // one already in progress or ready stays as it is (another batch or job may serve it too). Anything else
    // (placed, cancelled, delivered, completed) cannot be worked on: 409.
    public void lockForWork() {
        if (status == OrderStatus.CONFIRMED) {
            startWork();
        } else if (status != OrderStatus.IN_PROGRESS && status != OrderStatus.READY) {
            throw new InvalidStateTransitionException("Order " + orderNumber, status, OrderStatus.IN_PROGRESS);
        }
    }

    // IN_PROGRESS -> READY, or CONFIRMED -> READY when filled from existing stock (plan decision R5, FR-ORD-10).
    public void markReady() {
        if (status != OrderStatus.IN_PROGRESS && status != OrderStatus.CONFIRMED) {
            throw new InvalidStateTransitionException("Order " + orderNumber, status, OrderStatus.READY);
        }
        status = OrderStatus.READY;
        items.forEach(i -> i.moveTo(OrderItemStatus.READY));
    }

    // READY -> DELIVERED: all lines leave together, never part of a line (FR-ORD-11/12). The stock movements are
    // written by the service in the same transaction.
    public void deliver(LocalDate deliveryDate) {
        if (status != OrderStatus.READY) {
            throw new InvalidStateTransitionException("Order " + orderNumber, status, OrderStatus.DELIVERED);
        }
        status = OrderStatus.DELIVERED;
        deliveredOn = deliveryDate;
        items.forEach(i -> i.moveTo(OrderItemStatus.DELIVERED));
    }

    // Per-item or partial delivery (FR-ORD-19, E-ORD-10). Order cannot be PLACED or CANCELLED.
    public void deliverItem(OrderItem item, boolean fullyDelivered, LocalDate deliveryDate) {
        if (status == OrderStatus.PLACED || status == OrderStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Order " + orderNumber, status, OrderStatus.DELIVERED);
        }
        if (fullyDelivered) {
            item.moveTo(OrderItemStatus.DELIVERED);
        }
        boolean allDelivered = items.stream().allMatch(i -> i.getStatus() == OrderItemStatus.DELIVERED);
        if (allDelivered) {
            status = OrderStatus.DELIVERED;
            deliveredOn = deliveryDate;
        }
    }

    // DELIVERED -> COMPLETED, set explicitly and independent of payment (FR-ORD-13).
    public void complete() {
        transition(OrderStatus.DELIVERED, OrderStatus.COMPLETED);
    }

    // PLACED | CONFIRMED -> CANCELLED (FR-ORD-09). Locked orders: ORDER_LOCKED. Delivered, completed or cancelled
    // orders, and orders with payment allocations ("reverse the payment first"): INVALID_STATE_TRANSITION.
    public void cancel(boolean hasAllocations) {
        if (status == OrderStatus.IN_PROGRESS || status == OrderStatus.READY) {
            throw new OrderLockedException(orderNumber, "work has started");
        }
        if (status != OrderStatus.PLACED && status != OrderStatus.CONFIRMED) {
            throw new InvalidStateTransitionException("Order " + orderNumber, status, OrderStatus.CANCELLED);
        }
        if (hasAllocations) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Order " + orderNumber
                    + " has payments allocated to it; reverse the payment first.");
        }
        status = OrderStatus.CANCELLED;
        items.forEach(i -> i.moveTo(OrderItemStatus.CANCELLED));
    }

    // ---- derived values -------------------------------------------------------------------

    // Locked = production or outsourcing has started, or later (no edit or cancel).
    public boolean isLocked() {
        return status.isLocked();
    }

    // The single "may this order still be changed" check (DOMAIN_RULES 4, Orders): PLACED or CONFIRMED,
    // not locked, and nothing paid against it.
    public boolean canModify(boolean hasAllocations) {
        return (status == OrderStatus.PLACED || status == OrderStatus.CONFIRMED) && !hasAllocations;
    }

    // Late = the expected date has passed and the goods have not left (FR-ORD-16); never for cancelled orders.
    public boolean isLate(LocalDate today) {
        return requiredDate != null && requiredDate.isBefore(today) && status.isPending();
    }

    // Sum of the rounded line amounts (assumption Q5: no rounding of the total itself).
    public BigDecimal totalAmount() {
        return items.stream().map(OrderItem::getAmount).reduce(Money.zero(), BigDecimal::add);
    }

    // ---- helpers --------------------------------------------------------------------------

    private void requireModifiable(boolean hasAllocations) {
        if (isLocked()) {
            throw new OrderLockedException(orderNumber, "work has started");
        }
        if (hasAllocations) {
            throw new OrderLockedException(orderNumber, "payments are allocated to it");
        }
    }

    private void transition(OrderStatus from, OrderStatus to) {
        if (status != from) {
            throw new InvalidStateTransitionException("Order " + orderNumber, status, to);
        }
        status = to;
    }

    private void setRequiredDate(LocalDate requiredDate) {
        if (requiredDate != null && requiredDate.isBefore(orderDate)) {
            throw new FieldValidationException("requiredDate", "The expected delivery date cannot be before the order date.");
        }
        this.requiredDate = requiredDate;
    }

    // Existing lines are overwritten in place (same id, same line number), new ones appended, surplus ones removed.
    private void replaceLines(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new FieldValidationException("items", "An order needs at least one item.");
        }
        for (int i = 0; i < lines.size(); i++) {
            if (i < items.size()) {
                items.get(i).apply(lines.get(i));
            } else {
                items.add(OrderItem.of(this, i + 1, lines.get(i)));
            }
        }
        while (items.size() > lines.size()) {
            items.remove(items.size() - 1);
        }
    }

    // ---- getters --------------------------------------------------------------------------

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public LocalDate getRequiredDate() {
        return requiredDate;
    }

    public LocalDate getDeliveredOn() {
        return deliveredOn;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}

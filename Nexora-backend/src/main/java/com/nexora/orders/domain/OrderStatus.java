package com.nexora.orders.domain;

// Operational status of a customer order (DOMAIN_RULES section 5). Payment status is separate and derived.
public enum OrderStatus {
    PLACED,
    CONFIRMED,
    IN_PROGRESS,
    READY,
    DELIVERED,
    COMPLETED,
    CANCELLED;

    // ASSUMPTION Q2 (confirm with the owner): the customer owes the items from confirmation on, so
    // CONFIRMED..COMPLETED orders are obligations; PLACED and CANCELLED orders owe nothing.
    public boolean isObligation() {
        return this != PLACED && this != CANCELLED;
    }

    // Production or outsourcing has started (or the goods are gone): no edit or cancel (FR-ORD-08).
    public boolean isLocked() {
        return this == IN_PROGRESS || this == READY || this == DELIVERED || this == COMPLETED;
    }

    // Still to be delivered: the "pending" list filter (FR-ORD-05).
    public boolean isPending() {
        return this == PLACED || this == CONFIRMED || this == IN_PROGRESS || this == READY;
    }
}

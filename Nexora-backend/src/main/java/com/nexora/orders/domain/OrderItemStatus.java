package com.nexora.orders.domain;

// Status of one order line (04-DATA-MODEL section 15). An item is delivered whole, never in pieces.
public enum OrderItemStatus {
    PENDING,
    IN_PROGRESS,
    READY,
    DELIVERED,
    CANCELLED
}

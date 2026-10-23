package com.nexora.orders.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;
import com.nexora.orders.domain.OrderItemStatus;
import com.nexora.shared.domain.PaymentStatus;

// One order line with its derived payment figures (05-API-SPEC section 8).
public record OrderItemResponse(
        UUID id,
        int lineNo,
        ProductType productType,
        BigDecimal requiredWeightKg,
        BigDecimal ratePerKg,                 // historical, never recalculated
        BigDecimal amount,                    // round(weight x rate, 2), frozen
        String warpSpecification,
        OrderItemStatus status,
        BigDecimal allocatedAmount,           // allocations of active payments
        BigDecimal outstandingAmount,         // amount - allocated while the order is an obligation (Q2), else 0.00
        PaymentStatus paymentStatus,
        String notes) {
}

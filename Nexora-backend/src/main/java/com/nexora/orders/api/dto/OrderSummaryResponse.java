package com.nexora.orders.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.orders.domain.OrderStatus;
import com.nexora.parties.application.PartyRef;
import com.nexora.shared.domain.PaymentStatus;

// One row of the order list (E-ORD-02).
public record OrderSummaryResponse(
        UUID id,
        String orderNumber,
        PartyRef customer,
        LocalDate orderDate,
        LocalDate requiredDate,
        OrderStatus status,
        BigDecimal totalAmount,
        BigDecimal outstandingAmount,
        PaymentStatus paymentStatus,
        boolean late,
        int itemCount) {
}

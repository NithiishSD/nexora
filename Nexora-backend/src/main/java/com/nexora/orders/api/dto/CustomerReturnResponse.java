package com.nexora.orders.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;

public record CustomerReturnResponse(
        UUID id,
        String returnNumber,
        UUID orderId,
        UUID orderItemId,
        int lineNo,
        ProductType productType,
        LocalDate returnDate,
        BigDecimal returnedWeightKg,
        BigDecimal ratePerKg,
        BigDecimal creditedAmount,
        boolean usable,
        String movementNumber,
        String reason,
        String notes,
        Instant createdAt) {
}

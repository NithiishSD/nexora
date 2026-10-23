package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// One material receipt (05-API-SPEC section 6, E-PROC-08).
public record ReceiptResponse(
        UUID id,
        String receiptNumber,
        UUID purchaseId,
        LocalDate receiptDate,
        BigDecimal receivedWeightKg,
        BigDecimal acceptedWeightKg,
        BigDecimal rejectedWeightKg,
        String movementNumber,        // the RECEIPT movement; null when nothing was accepted
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {
}

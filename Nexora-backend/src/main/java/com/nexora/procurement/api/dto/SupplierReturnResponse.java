package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// One return to the supplier (05-API-SPEC section 6, E-PROC-09).
public record SupplierReturnResponse(
        UUID id,
        String returnNumber,
        UUID purchaseId,
        LocalDate returnDate,
        BigDecimal returnedWeightKg,
        String reason,
        String movementNumber,        // the PURCHASE_RETURN movement
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {
}

package com.nexora.inventory.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

// One ledger row as the API shows it (05-API-SPEC section 7). Read-only: no endpoint accepts it as input.
public record MovementResponse(
        UUID id,
        String movementNumber,
        MovementType movementType,
        ProductType productType,              // leaving fromLocation
        ProductType toProductType,            // arriving at toLocation
        BigDecimal quantityKg,
        StockLocation fromLocation,           // null for OPENING_BALANCE
        StockLocation toLocation,
        LocalDate movementDate,
        MovementReferenceType referenceType,
        UUID referenceId,
        String referenceNumber,               // e.g. RCT-2026-0004, or null
        String reason,
        String notes,
        Instant createdAt,
        UUID createdBy) {
}

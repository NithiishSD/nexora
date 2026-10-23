package com.nexora.inventory.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexora.inventory.domain.InventoryThreshold;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

public record InventoryThresholdResponse(
        UUID id,
        ProductType productType,
        StockLocation location,
        BigDecimal minThresholdKg,
        Instant createdAt,
        Instant updatedAt) {

    public static InventoryThresholdResponse from(InventoryThreshold t) {
        return new InventoryThresholdResponse(
                t.getId(),
                t.getProductType(),
                t.getLocation(),
                t.getMinThresholdKg(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}

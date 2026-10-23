package com.nexora.inventory.api.dto;

import java.math.BigDecimal;

import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

public record LowStockItemResponse(
        ProductType productType,
        StockLocation location,
        BigDecimal currentQuantityKg,
        BigDecimal minThresholdKg,
        BigDecimal deficitKg) {
}

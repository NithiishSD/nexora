package com.nexora.inventory.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/inventory/opening-balances (E-INV-07): stock counted at go-live, once per product and
// internal location. Not a purchase: no supplier, no money.
public record OpeningStockRequest(
        @NotNull ProductType productType,
        @NotNull StockLocation location,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantityKg,
        @NotNull @BusinessDate LocalDate movementDate,
        @Size(max = 1000) String notes) {
}

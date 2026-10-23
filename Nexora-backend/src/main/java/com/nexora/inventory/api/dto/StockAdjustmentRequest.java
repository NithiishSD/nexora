package com.nexora.inventory.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/inventory/adjustments (E-INV-06): a correction after a physical count, owner only.
// Direction comes from the two locations (at least one internal); the quantity is always positive.
public record StockAdjustmentRequest(
        @NotNull ProductType productType,
        @NotNull StockLocation fromLocation,
        @NotNull StockLocation toLocation,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantityKg,
        @NotNull @BusinessDate LocalDate movementDate,
        @NotBlank @Size(max = 255) String reason,
        @Size(max = 1000) String notes) {
}

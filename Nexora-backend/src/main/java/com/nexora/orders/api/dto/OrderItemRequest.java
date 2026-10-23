package com.nexora.orders.api.dto;

import java.math.BigDecimal;

import com.nexora.inventory.domain.ProductType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// One requested line (05-API-SPEC section 8). No amount field: the backend computes it (FR-ORD-02 AC3).
public record OrderItemRequest(
        @NotNull ProductType productType,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal requiredWeightKg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal ratePerKg,
        @Size(max = 500) String warpSpecification,
        @Size(max = 1000) String notes) {
}

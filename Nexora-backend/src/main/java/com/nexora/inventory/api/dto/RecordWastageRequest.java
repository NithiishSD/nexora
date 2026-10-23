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

// Body of POST /api/v1/inventory/wastage (E-INV-05): material lost outside production completion.
// The source must be RAW_STOCK, INTERNAL_WIP or EXTERNAL_WIP (checked by the movement rules).
public record RecordWastageRequest(
        @NotNull ProductType productType,
        @NotNull StockLocation fromLocation,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantityKg,   // more than 3 decimals -> 400
        @NotNull @BusinessDate LocalDate movementDate,
        @NotBlank @Size(max = 255) String reason) {
}

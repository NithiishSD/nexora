package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;
import com.nexora.procurement.domain.PaymentType;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/purchases (E-PROC-01). Only facts the client knows: no number, status, value or due
// date (the backend computes those; an extra "orderedValue" field in the JSON is simply ignored, FR-PROC-03 AC2).
public record CreatePurchaseRequest(
        @NotNull UUID supplierId,
        ProductType productType,                                                     // default RAW_SILK
        @NotNull @BusinessDate LocalDate purchaseDate,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal orderedWeightKg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal ratePerKg,
        @NotNull PaymentType paymentType,
        @PositiveOrZero @Max(365) Integer creditDays,     // CREDIT default: the supplier's default credit days
        @Size(max = 100) String billReference,
        @Size(max = 1000) String notes) {
}

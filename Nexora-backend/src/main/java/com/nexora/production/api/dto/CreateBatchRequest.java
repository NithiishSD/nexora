package com.nexora.production.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.inventory.domain.ProductType;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/production-batches (E-PROD-01). A plan only: no stock moves until the batch starts.
public record CreateBatchRequest(
        @NotNull ProductType productType,                    // SILK_WARP or KORA_WARP (else 422)
        @NotNull @BusinessDate LocalDate productionDate,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal inputWeightKg,
        @Size(max = 1000) String notes,
        @Size(max = 50) List<@Valid @NotNull BatchLinkRequest> orderItems) {
}

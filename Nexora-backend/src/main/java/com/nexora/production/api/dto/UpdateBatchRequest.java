package com.nexora.production.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of PATCH /api/v1/production-batches/{id} (E-PROD-04), only while PLANNED. A missing field keeps its value, ""
// clears the notes; orderItems, when sent, is the full new list. The product type cannot change.
public record UpdateBatchRequest(
        @BusinessDate LocalDate productionDate,
        @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal inputWeightKg,
        @Size(max = 1000) String notes,
        @Size(max = 50) List<@Valid @NotNull BatchLinkRequest> orderItems) {
}

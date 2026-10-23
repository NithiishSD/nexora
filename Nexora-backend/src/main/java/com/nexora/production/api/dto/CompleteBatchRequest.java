package com.nexora.production.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/production-batches/{id}/complete (E-PROD-06). input = output + wastage + discrepancy exactly,
// else 422 PRODUCTION_NOT_RECONCILED. completionDate defaults to today; notes, when sent, replace the batch's notes.
public record CompleteBatchRequest(
        @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal outputWeightKg,
        @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal wastageWeightKg,
        @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal discrepancyWeightKg,
        @BusinessDate LocalDate completionDate,
        @Size(max = 1000) String notes) {
}

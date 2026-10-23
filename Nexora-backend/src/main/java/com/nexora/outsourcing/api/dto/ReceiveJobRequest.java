package com.nexora.outsourcing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/outsourcing-jobs/{id}/receive (E-OUT-05): warp back from the manufacturer and the loss or
// difference closed with it. Cumulative received + wastage may not pass the issued weight (409).
public record ReceiveJobRequest(
        @NotNull @BusinessDate LocalDate receiptDate,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal receivedWeightKg,
        @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal wastageWeightKg,
        @Size(max = 1000) String notes) {
}

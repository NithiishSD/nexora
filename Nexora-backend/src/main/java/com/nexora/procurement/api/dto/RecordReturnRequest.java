package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/purchases/{id}/returns (E-PROC-07): material sent back to the supplier.
public record RecordReturnRequest(
        @NotNull @BusinessDate LocalDate returnDate,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal returnedWeightKg,
        @NotBlank @Size(max = 255) String reason,
        @Size(max = 1000) String notes) {
}

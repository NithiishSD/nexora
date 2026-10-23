package com.nexora.chits.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/chits (E-CHIT-01): a new chit, or one running at go-live with the contributions paid before.
public record CreateChitRequest(
        @NotBlank @Size(max = 150) String chitName,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal totalAmount,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal monthlyAmount,
        @NotNull @Min(1) @Max(120) Integer durationMonths,
        @NotNull @BusinessDate LocalDate startDate,
        @Size(max = 1000) String notes,
        @Min(0) Integer priorContributionsCount,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal priorContributionsAmount) {
}

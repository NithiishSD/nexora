package com.nexora.workforce.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.shared.api.BusinessDate;
import com.nexora.workforce.domain.WorkType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/work-records (E-WORK-01). hours for ROLLING, quantityKg for WARPING, never both (checked by the
// record); rate defaults to the worker's rate for the type. amount and status are never accepted.
public record RecordWorkRequest(
        @NotNull UUID workerId,
        @NotNull @BusinessDate LocalDate workDate,
        @NotNull WorkType workType,
        @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal hours,
        @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantityKg,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal rate,
        UUID productionBatchId,
        @Size(max = 1000) String notes) {
}

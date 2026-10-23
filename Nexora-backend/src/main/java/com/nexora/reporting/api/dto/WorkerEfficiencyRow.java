package com.nexora.reporting.api.dto;

import java.math.BigDecimal;

import com.nexora.parties.application.PartyRef;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Worker efficiency metrics over a date range (FR-WORK-10, E-WORK-05)")
public record WorkerEfficiencyRow(
        @Schema(description = "Worker reference")
        PartyRef worker,
        @Schema(description = "Output rate in kg per hour (scale 2)", example = "3.20")
        BigDecimal kgPerHour,
        @Schema(description = "Total hours worked (scale 2)", example = "40.00")
        BigDecimal hours,
        @Schema(description = "Total output in kg (scale 3)", example = "128.000")
        BigDecimal kg
) {}

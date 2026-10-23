package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Profitability line broken down by dimension (FR-RPT-17, E-RPT-16)")
public record ProfitabilityReportLine(
        @Schema(description = "Dimension key: product name, customer name, or manufacturer name", example = "SILK_WARP")
        String key,
        @Schema(description = "Entity id (party id for customer or manufacturer, null for product)")
        UUID id,
        @Schema(description = "Revenue generated", example = "150000.00")
        BigDecimal revenue,
        @Schema(description = "Cost incurred or attributed", example = "105000.00")
        BigDecimal cost,
        @Schema(description = "Gross margin (revenue - cost)", example = "45000.00")
        BigDecimal margin,
        @Schema(description = "Margin percentage (margin / revenue * 100, scale 2)", example = "30.00")
        BigDecimal marginPercent
) {}

package com.nexora.reporting.api.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Profit and Loss statement line for a period (FR-RPT-17, E-RPT-15)")
public record ProfitAndLossReportLine(
        @Schema(description = "Period identifier (yyyy-MM or yyyy)", example = "2026-10")
        String period,
        @Schema(description = "Delivered sales revenue", example = "420000.00")
        BigDecimal revenue,
        @Schema(description = "Operating business expenses (excluding personal drawings)", example = "45000.00")
        BigDecimal businessExpenses,
        @Schema(description = "Financing interest cost on loans", example = "12000.00")
        BigDecimal interestCost,
        @Schema(description = "Production cost (raw materials + worker wages + outsourcing)", example = "302000.00")
        BigDecimal productionCost,
        @Schema(description = "Net profit for the period (revenue - expenses - interest - production)", example = "61000.00")
        BigDecimal profit
) {}

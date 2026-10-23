package com.nexora.chits.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Chit auction bid and dividend calculation result (FR-CHIT-10, E-CHIT-09)")
public record ChitBidResponse(
        @Schema(description = "Chit ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID chitId,
        @Schema(description = "Chit business number", example = "CHT-2026-0001")
        String chitNumber,
        @Schema(description = "Auction month", example = "2027-03")
        String month,
        @Schema(description = "Auction bid discount amount", example = "40000.00")
        BigDecimal bidAmount,
        @Schema(description = "Duration in months / number of members", example = "20")
        int durationMonths,
        @Schema(description = "Calculated dividend per member", example = "2000.00")
        BigDecimal dividendPerMember,
        @Schema(description = "Standard monthly installment amount without dividend", example = "25000.00")
        BigDecimal standardMonthlyAmount,
        @Schema(description = "Net installment contribution due after deducting dividend", example = "23000.00")
        BigDecimal contributionDue
) {}

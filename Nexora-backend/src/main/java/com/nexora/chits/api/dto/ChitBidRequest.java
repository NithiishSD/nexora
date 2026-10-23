package com.nexora.chits.api.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Monthly chit auction bid request (FR-CHIT-10, E-CHIT-09)")
public record ChitBidRequest(
        @NotNull
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Month must be in yyyy-MM format")
        @Schema(description = "Auction month in yyyy-MM format", example = "2027-03")
        String month,

        @NotNull
        @DecimalMin("0.00")
        @Schema(description = "Auction bid discount amount", example = "40000.00")
        BigDecimal bidAmount
) {}

package com.nexora.loans.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Loan repayment advice and options (FR-LOAN-10, E-LOAN-09)")
public record LoanAdviceResponse(
        @Schema(description = "Loan id", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID loanId,
        @Schema(description = "Loan business number", example = "LON-2026-0001")
        String loanNumber,
        @Schema(description = "Outstanding principal balance", example = "400000.00")
        BigDecimal outstandingPrincipal,
        @Schema(description = "Configured interest rate percent", example = "1.500")
        BigDecimal interestRatePercent,
        @Schema(description = "Suggested monthly interest due", example = "5800.00")
        BigDecimal interestDueSuggestion,
        @Schema(description = "Total available funds across active accounts", example = "150000.00")
        BigDecimal totalAvailableFunds,
        @Schema(description = "Repayment options with projected available funds after payment")
        List<LoanAdviceOption> options
) {
    public record LoanAdviceOption(
            @Schema(description = "Action type: PAY_INTEREST, PART_PRINCIPAL, FULL_CLOSURE", example = "PAY_INTEREST")
            String action,
            @Schema(description = "Recommended payment amount", example = "5800.00")
            BigDecimal amount,
            @Schema(description = "Projected remaining available funds after payment", example = "144200.00")
            BigDecimal availableAfter,
            @Schema(description = "Human-readable description / advice", example = "Pay current interest of 5,800.00 to keep loan in good standing")
            String description
    ) {}
}

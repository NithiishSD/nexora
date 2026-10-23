package com.nexora.loans.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.loans.domain.LoanType;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/loans/existing (E-LOAN-02): a loan already running at go-live and what is still owed on it.
// No money moves.
public record ExistingLoanRequest(
        @NotNull UUID lenderId,
        @NotNull LoanType loanType,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal principalAmount,
        @NotNull @BusinessDate LocalDate startDate,
        LocalDate dueDate,
        @DecimalMin("0.000") @Digits(integer = 3, fraction = 3) BigDecimal interestRatePercent,
        @Size(max = 500) String interestNotes,
        @Size(max = 1000) String notes,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal outstandingPrincipal) {
}

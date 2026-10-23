package com.nexora.loans.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.PaymentMethod;
import com.nexora.loans.domain.LoanType;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/loans (E-LOAN-01): the loan terms plus the account the money arrives in.
public record CreateLoanRequest(
        @NotNull UUID lenderId,
        @NotNull LoanType loanType,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal principalAmount,
        @NotNull @BusinessDate LocalDate startDate,
        LocalDate dueDate,
        @DecimalMin("0.000") @Digits(integer = 3, fraction = 3) BigDecimal interestRatePercent,
        @Size(max = 500) String interestNotes,
        @Size(max = 1000) String notes,
        @NotNull UUID financialAccountId,
        @NotNull PaymentMethod paymentMethod) {
}

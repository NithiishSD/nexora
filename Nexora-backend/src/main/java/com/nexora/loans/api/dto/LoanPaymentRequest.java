package com.nexora.loans.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.PaymentMethod;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/loans/{id}/principal-repayments and /interest-payments (E-LOAN-05/06). periodNote is used for
// interest ("Oct 2026"). The method is checked against the account but not stored (the ledger row has no column).
public record LoanPaymentRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull UUID financialAccountId,
        @NotNull PaymentMethod paymentMethod,
        @NotNull @BusinessDate LocalDate paymentDate,
        @Size(max = 100) String periodNote,
        @Size(max = 500) String notes) {
}

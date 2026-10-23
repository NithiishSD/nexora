package com.nexora.chits.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.PaymentMethod;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of a chit contribution, payout or organiser payment (05 CashEntry): physical cash only, so a CASH account and
// the CASH method (else 422 INVALID_PAYMENT_METHOD).
public record ChitEntryRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull UUID financialAccountId,
        @NotNull PaymentMethod paymentMethod,
        @NotNull @BusinessDate LocalDate entryDate,
        @Size(max = 1000) String notes) {
}

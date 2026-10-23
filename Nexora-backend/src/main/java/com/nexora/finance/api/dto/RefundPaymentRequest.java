package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.PaymentMethod;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/payments/{id}/refunds (E-PAY-13). Returns advance money to a customer.
public record RefundPaymentRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull PaymentMethod paymentMethod,
        @NotNull UUID financialAccountId,
        @BusinessDate LocalDate refundDate,
        @NotBlank @Size(max = 255) String reason) {
}

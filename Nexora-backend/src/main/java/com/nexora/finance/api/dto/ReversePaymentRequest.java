package com.nexora.finance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/payments/{id}/reverse (E-PAY-05): why the payment is undone (kept on the payment and in the
// audit log).
public record ReversePaymentRequest(@NotBlank @Size(max = 255) String reason) {
}

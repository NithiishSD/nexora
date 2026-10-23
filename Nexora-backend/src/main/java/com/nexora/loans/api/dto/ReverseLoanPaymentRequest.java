package com.nexora.loans.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/loans/{id}/payments/{transactionId}/reverse (E-LOAN-07).
public record ReverseLoanPaymentRequest(@NotBlank @Size(max = 255) String reason) {
}

package com.nexora.finance.api.dto;

import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.Size;

// Body of POST /api/v1/payments/{id}/cheque-clearance and /cheque-bounce (E-PAY-14).
public record ChequeActionRequest(
        @BusinessDate LocalDate date,
        @Size(max = 255) String notes) {
}

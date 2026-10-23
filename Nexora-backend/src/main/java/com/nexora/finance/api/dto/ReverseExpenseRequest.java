package com.nexora.finance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/expenses/{id}/reverse (E-FIN-16): why the expense is undone (kept on it and in the audit log).
public record ReverseExpenseRequest(@NotBlank @Size(max = 255) String reason) {
}

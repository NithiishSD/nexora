package com.nexora.chits.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/chits/{id}/entries/{transactionId}/reverse (E-CHIT-07).
public record ReverseChitEntryRequest(@NotBlank @Size(max = 255) String reason) {
}

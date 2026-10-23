package com.nexora.procurement.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/purchases/{id}/cancel (E-PROC-05): why the purchase is cancelled.
public record CancelPurchaseRequest(@NotBlank @Size(max = 255) String reason) {
}

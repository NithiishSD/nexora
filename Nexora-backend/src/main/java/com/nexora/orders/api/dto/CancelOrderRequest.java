package com.nexora.orders.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/orders/{id}/cancel (E-ORD-06): why the order is cancelled (kept in the audit log).
public record CancelOrderRequest(@NotBlank @Size(max = 255) String reason) {
}

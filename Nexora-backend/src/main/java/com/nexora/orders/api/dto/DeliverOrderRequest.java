package com.nexora.orders.api.dto;

import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/orders/{id}/deliver (E-ORD-08). There is no quantity: every item leaves whole (FR-ORD-12);
// a quantity sent by a client is ignored. The notes are stored on each stock movement.
public record DeliverOrderRequest(
        @NotNull @BusinessDate LocalDate deliveryDate,
        @Size(max = 1000) String notes) {
}

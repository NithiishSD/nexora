package com.nexora.orders.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/orders (E-ORD-01). Status, number, amounts and payment fields are never accepted.
public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotNull @BusinessDate LocalDate orderDate,
        @BusinessDate LocalDate requiredDate,              // expected delivery; not before orderDate (checked by the order)
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull OrderItemRequest> items) {
}

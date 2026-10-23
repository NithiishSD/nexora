package com.nexora.orders.api.dto;

import java.time.LocalDate;
import java.util.List;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of PATCH /api/v1/orders/{id} (E-ORD-04). A missing (null) field keeps its value; an empty notes string clears
// the notes; items, when sent, are the full new list and the amounts are recalculated. customerId, orderDate,
// status, paymentStatus and amounts are not fields here, so a client sending them changes nothing.
public record UpdateOrderRequest(
        @BusinessDate LocalDate requiredDate,
        @Size(max = 1000) String notes,
        @Size(min = 1, max = 50) List<@Valid @NotNull OrderItemRequest> items) {
}

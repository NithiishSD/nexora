package com.nexora.orders.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// E-ORD-10 request body: per-item or partial delivery.
public record DeliverItemRequest(
        @NotNull @BusinessDate
        LocalDate deliveryDate,

        @NotNull(message = "Quantity in kg is required.")
        @DecimalMin(value = "0.001", message = "Quantity must be greater than 0.")
        @Digits(integer = 9, fraction = 3, message = "Quantity can have at most 3 decimal places.")
        BigDecimal quantityKg,

        @Size(max = 1000, message = "Notes cannot exceed 1000 characters.")
        String notes) {
}

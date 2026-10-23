package com.nexora.orders.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordCustomerReturnRequest(
        @NotNull(message = "orderItemId is required")
        UUID orderItemId,

        @NotNull(message = "returnDate is required")
        @BusinessDate
        LocalDate returnDate,

        @NotNull(message = "returnedWeightKg is required")
        @DecimalMin(value = "0.001", message = "returnedWeightKg must be greater than 0")
        @Digits(integer = 9, fraction = 3, message = "returnedWeightKg can have at most 3 decimals")
        BigDecimal returnedWeightKg,

        boolean usable,

        @NotBlank(message = "reason is required")
        @Size(max = 255, message = "reason cannot exceed 255 characters")
        String reason,

        String notes) {
}

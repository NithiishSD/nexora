package com.nexora.inventory.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record SetThresholdRequest(
        @NotNull(message = "Minimum threshold is required.")
        @DecimalMin(value = "0.000", message = "Minimum threshold must be 0 kg or greater.")
        BigDecimal minThresholdKg) {
}

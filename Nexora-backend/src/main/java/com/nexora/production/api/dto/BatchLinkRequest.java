package com.nexora.production.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

// One order item a batch serves, with the kg meant for it.
public record BatchLinkRequest(
        @NotNull UUID orderItemId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal allocatedWeightKg) {
}

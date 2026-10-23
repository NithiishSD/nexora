package com.nexora.outsourcing.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

// One order item a job serves, with the kg meant for it (the same rules as a batch link).
public record JobLinkRequest(
        @NotNull UUID orderItemId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal allocatedWeightKg) {
}

package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.nexora.finance.domain.AllocationTargetType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

// One explicit allocation chosen by the client; validated again by the backend (FR-PAY-03).
public record AllocationRequest(
        @NotNull AllocationTargetType targetType,
        @NotNull UUID targetId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
}

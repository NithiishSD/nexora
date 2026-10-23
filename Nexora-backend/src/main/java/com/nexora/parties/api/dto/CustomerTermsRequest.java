package com.nexora.parties.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Body of PATCH /api/v1/parties/{id}/customer-terms (E-PARTY-08, FR-PARTY-17).
public record CustomerTermsRequest(
        @NotNull @PositiveOrZero @Max(365) Integer creditPeriodDays) {
}

package com.nexora.outsourcing.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/outsourcing-jobs (E-OUT-01). No stock moves until the job is issued; order links are optional.
public record CreateJobRequest(
        @NotNull UUID manufacturerId,
        @NotNull ProductType productType,                    // SILK_WARP or KORA_WARP (else 422)
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal issuedWeightKg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal ratePerKg,
        @Size(max = 1000) String notes,
        @Size(max = 50) List<@Valid @NotNull JobLinkRequest> orderItems) {
}

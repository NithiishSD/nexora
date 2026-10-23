package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.finance.domain.Direction;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/finance/accounts/{id}/adjustments (E-FIN-05): a correction after a count or a statement. The
// reason is stored as the ledger row's description and in the audit log.
public record AccountAdjustmentRequest(
        @NotNull Direction direction,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull @BusinessDate LocalDate transactionDate,
        @NotBlank @Size(max = 255) String reason) {
}

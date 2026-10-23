package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// One transfer between own accounts (E-FIN-09) with its two ledger rows.
public record TransferResponse(
        UUID id,
        String transferNumber,
        AccountRef sourceAccount,
        AccountRef destinationAccount,
        BigDecimal amount,
        LocalDate transferDate,
        UUID outTransactionId,
        UUID inTransactionId,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {
}

package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;

// One ledger row (05-API-SPEC section 10). Rows never change, so there is no updatedAt.
public record TransactionResponse(
        UUID id,
        AccountRef account,
        LocalDate transactionDate,
        TransactionType transactionType,
        Direction direction,
        BigDecimal amount,
        TransactionReferenceType referenceType,
        UUID referenceId,
        String referenceNumber,
        String description,
        UUID reversesTransactionId,         // on REVERSAL rows
        UUID reversedByTransactionId,       // on rows that were reversed
        Instant createdAt,
        UUID createdBy) {
}

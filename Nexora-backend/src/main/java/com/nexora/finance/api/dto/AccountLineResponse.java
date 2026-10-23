package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;

// One row of an account's history (E-FIN-06): a TransactionResponse plus the account balance right after it.
public record AccountLineResponse(
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
        UUID reversesTransactionId,
        UUID reversedByTransactionId,
        Instant createdAt,
        UUID createdBy,
        BigDecimal runningBalance) {

    public static AccountLineResponse of(TransactionResponse t, BigDecimal runningBalance) {
        return new AccountLineResponse(t.id(), t.account(), t.transactionDate(), t.transactionType(), t.direction(),
                t.amount(), t.referenceType(), t.referenceId(), t.referenceNumber(), t.description(),
                t.reversesTransactionId(), t.reversedByTransactionId(), t.createdAt(), t.createdBy(), runningBalance);
    }
}

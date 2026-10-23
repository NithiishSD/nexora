package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;

// What a use case asks the ledger to record: intent only. The balance check and the row are FinanceService's job.
// referenceType/referenceId name the one business record behind the money (NONE/null for opening balances and
// adjustments); referenceNumber is its business number or a cheque/UTR number.
public record TransactionCommand(
        UUID accountId,
        Direction direction,
        BigDecimal amount,
        TransactionType transactionType,
        LocalDate transactionDate,
        String description,
        String referenceNumber,
        TransactionReferenceType referenceType,
        UUID referenceId) {
}

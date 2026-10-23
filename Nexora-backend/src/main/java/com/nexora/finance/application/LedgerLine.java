package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.api.dto.AccountRef;
import com.nexora.finance.domain.TransactionType;

// One ledger row of a business record (a loan, a chit) for that module's own views. reversalTransactionId is set when
// the row was reversed; REVERSAL rows themselves are not listed.
public record LedgerLine(UUID transactionId, UUID referenceId, TransactionType transactionType, BigDecimal amount,
                         LocalDate date, AccountRef account, String description, UUID reversalTransactionId) {

    public boolean reversed() {
        return reversalTransactionId != null;
    }
}

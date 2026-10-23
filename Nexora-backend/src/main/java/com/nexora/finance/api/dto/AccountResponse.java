package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexora.finance.domain.AccountStatus;
import com.nexora.finance.domain.AccountType;

// One account with its derived balance (E-FIN-03). maskedAccountNumber is XXXXXX1234 or null.
public record AccountResponse(
        UUID id,
        String accountName,
        AccountType accountType,
        String bankName,
        String maskedAccountNumber,
        AccountStatus status,
        BigDecimal balance,                 // SUM(IN) - SUM(OUT) of its transactions
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {
}

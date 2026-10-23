package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.finance.domain.Direction;

// Response of POST /api/v1/finance/accounts/{id}/statement-imports (E-FIN-18).
public record StatementImportResponse(
        UUID importId,
        UUID accountId,
        int totalRows,
        int suggestedMatches,
        List<StatementRowMatch> items) {

    public record StatementRowMatch(
            LocalDate statementDate,
            String description,
            BigDecimal amount,
            Direction direction,
            UUID matchedTransactionId) {
    }
}

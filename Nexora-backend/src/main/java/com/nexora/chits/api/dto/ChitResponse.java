package com.nexora.chits.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.chits.domain.ChitStatus;
import com.nexora.finance.domain.TransactionType;

// One chit (05-API-SPEC section 12). Counts and totals are derived from the ledger plus what was paid before go-live.
public record ChitResponse(
        UUID id,
        String chitNumber,
        String chitName,
        BigDecimal totalAmount,
        BigDecimal monthlyAmount,
        int durationMonths,
        LocalDate startDate,
        ChitStatus status,
        int priorContributionsCount,
        BigDecimal priorContributionsAmount,
        int contributionsCount,                // prior + recorded, net of reversals
        BigDecimal totalContributed,           // prior + recorded
        int remainingExpectedContributions,    // max(0, duration - contributionsCount)
        BigDecimal organiserPayments,
        BigDecimal payoutReceived,             // null until a payout counts
        List<Entry> entries,                   // detail only (null in lists)
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {

    public record Entry(UUID transactionId, TransactionType transactionType, BigDecimal amount, LocalDate date,
                        boolean reversed) {
    }
}

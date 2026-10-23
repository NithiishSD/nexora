package com.nexora.loans.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.finance.api.dto.AccountRef;
import com.nexora.finance.domain.TransactionType;
import com.nexora.loans.domain.LoanStatus;
import com.nexora.loans.domain.LoanType;
import com.nexora.parties.application.PartyRef;

// One loan (05-API-SPEC section 11). Repaid, interest and outstanding amounts are derived from the ledger.
public record LoanResponse(
        UUID id,
        String loanNumber,
        PartyRef lender,
        LoanType loanType,
        BigDecimal principalAmount,
        BigDecimal repaidBeforeGoLive,
        BigDecimal principalRepaid,            // net of reversals
        BigDecimal outstandingPrincipal,
        BigDecimal interestPaid,               // net of reversals
        LocalDate startDate,
        LocalDate dueDate,
        BigDecimal interestRatePercent,
        String interestNotes,
        LoanStatus status,
        boolean existingAtGoLive,
        UUID receivedTransactionId,            // null for a loan registered as existing
        List<Payment> payments,                // detail only (null in lists)
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {

    // paymentMethod is not stored on the ledger row: null except for cash accounts (decision T5.04).
    public record Payment(UUID transactionId, TransactionType transactionType, BigDecimal amount, LocalDate date,
                          AccountRef account, String paymentMethod, String periodNote, boolean reversed,
                          UUID reversalTransactionId) {
    }
}

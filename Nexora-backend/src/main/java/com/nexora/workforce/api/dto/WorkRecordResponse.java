package com.nexora.workforce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.RecordStatus;
import com.nexora.parties.application.PartyRef;
import com.nexora.shared.domain.PaymentStatus;
import com.nexora.workforce.domain.WorkType;

// One work record (05-API-SPEC section 15). allocated/pending/paymentStatus are derived; a REVERSED record shows
// UNPAID and is out of every payable.
public record WorkRecordResponse(
        UUID id,
        PartyRef worker,
        LocalDate workDate,
        WorkType workType,
        BigDecimal hours,                      // ROLLING only
        BigDecimal quantityKg,                 // WARPING only
        BigDecimal rate,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        BigDecimal pendingAmount,
        PaymentStatus paymentStatus,
        RecordStatus status,
        Instant reversedAt,
        String reversalReason,
        BatchRef productionBatch,              // null when not linked
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {

    public record BatchRef(UUID id, String batchNumber) {
    }
}

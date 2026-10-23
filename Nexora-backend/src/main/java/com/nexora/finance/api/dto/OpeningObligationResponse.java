package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.parties.application.PartyRef;

// One opening obligation with what has been paid against it (allocations of active payments).
public record OpeningObligationResponse(UUID id, PaymentPurpose purpose, PartyRef party, LocalDate obligationDate,
                                        LocalDate dueDate, BigDecimal originalAmount, BigDecimal allocated,
                                        BigDecimal outstanding, String referenceNote, Instant createdAt,
                                        Instant updatedAt, UUID createdBy) {
}

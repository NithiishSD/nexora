package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// An opening payable or receivable of one party with what is still open, for other modules' payable views.
public record OpeningBalanceLine(UUID id, LocalDate obligationDate, String referenceNote,
                                 BigDecimal originalAmount, BigDecimal allocated, BigDecimal outstanding) {
}

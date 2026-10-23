package com.nexora.outsourcing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.parties.application.PartyRef;
import com.nexora.shared.domain.PaymentStatus;

// GET /api/v1/manufacturers/{id}/payable (E-PAY-09): open jobs (received kg x historical rate - allocations) and open
// opening payables; totalPayable is the sum of what is outstanding.
public record ManufacturerPayableResponse(
        PartyRef manufacturer,
        BigDecimal totalPayable,
        List<JobPayable> jobs,                          // outstanding not 0, oldest issue first
        List<OpeningPayable> openingObligations) {

    public record JobPayable(UUID jobId, String jobNumber, LocalDate issueDate, BigDecimal receivedKg,
                             BigDecimal ratePerKg, BigDecimal payable, BigDecimal allocated, BigDecimal outstanding,
                             PaymentStatus paymentStatus) {
    }

    public record OpeningPayable(UUID id, LocalDate obligationDate, String referenceNote, BigDecimal originalAmount,
                                 BigDecimal allocated, BigDecimal outstanding) {
    }
}

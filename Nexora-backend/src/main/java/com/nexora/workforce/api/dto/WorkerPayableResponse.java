package com.nexora.workforce.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.parties.application.PartyRef;
import com.nexora.shared.domain.PaymentStatus;
import com.nexora.workforce.domain.WorkType;

// GET /api/v1/workers/{id}/payable (E-PAY-10): recorded work in the range (a payroll week, or all), what was paid on
// it and what is pending; reversed records are left out. Open opening payables are listed apart, not in the totals.
public record WorkerPayableResponse(
        PartyRef worker,
        LocalDate from,
        LocalDate to,
        BigDecimal workValue,
        BigDecimal paid,
        BigDecimal pending,
        List<Record> records,
        List<OpeningPayable> openingObligations) {

    public record Record(UUID workRecordId, LocalDate workDate, WorkType workType, BigDecimal amount,
                         BigDecimal allocated, BigDecimal outstanding, PaymentStatus paymentStatus) {
    }

    public record OpeningPayable(UUID id, LocalDate obligationDate, String referenceNote, BigDecimal originalAmount,
                                 BigDecimal allocated, BigDecimal outstanding) {
    }
}

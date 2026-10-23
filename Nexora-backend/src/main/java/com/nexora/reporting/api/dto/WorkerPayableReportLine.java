package com.nexora.reporting.api.dto;

import java.math.BigDecimal;

import com.nexora.parties.application.PartyRef;

// One worker of GET /api/v1/reports/worker-payable (E-RPT-13): recorded work in the period split by rolling and
// warping, what was paid on it and what is pending (as GET /workers/{id}/payable for the same range).
public record WorkerPayableReportLine(PartyRef worker, BigDecimal rollingEarned, BigDecimal warpingEarned,
                                      BigDecimal earned, BigDecimal paid, BigDecimal pending) {
}

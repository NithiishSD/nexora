package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.parties.application.PartyRef;

// GET /api/v1/reports/customer-outstanding (E-RPT-02). Totals cover every matching customer, not only this page.
public record CustomerOutstandingReport(LocalDate asOf, List<Row> rows, Totals totals, int page, int size,
                                        long totalElements, int totalPages) {

    // oldestAgeDays is null when nothing is unpaid (a customer with only an advance).
    public record Row(PartyRef customer, BigDecimal outstanding, BigDecimal advance, long unpaidOrders,
                      Integer oldestAgeDays) {
    }

    public record Totals(BigDecimal outstanding, BigDecimal advance) {
    }
}

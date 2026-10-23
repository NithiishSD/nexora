package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nexora.parties.application.PartyRef;

// GET /api/v1/reports/supplier-payables (E-RPT-04): what is still owed, per supplier, by how long it is overdue.
// buckets always carry NOT_DUE, D1_7, D8_15 and OVER_15.
public record SupplierPayablesReport(LocalDate asOf, List<SupplierGroup> suppliers, Map<String, BigDecimal> buckets,
                                     BigDecimal total) {

    public record SupplierGroup(PartyRef supplier, Map<String, BigDecimal> buckets, BigDecimal total,
                                List<Item> purchases) {
    }

    // purchaseId is null for an opening payable; its purchaseNumber is then "Opening SUP <date>".
    public record Item(UUID purchaseId, String purchaseNumber, LocalDate purchaseDate, LocalDate dueDate,
                       BigDecimal outstanding, int overdueDays, String bucket) {
    }
}

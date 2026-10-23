package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyRef;

// GET /api/v1/reports/purchases (E-RPT-05): purchases dated in the period, per supplier and product. Cancelled
// purchases are left out. payableValue follows assumption Q1 (accepted - returned kg at the purchase rate).
public record PurchaseReport(LocalDate from, LocalDate to, List<Row> rows, Totals totals) {

    public record Row(PartyRef supplier, ProductType productType, long purchases, BigDecimal orderedKg,
                      BigDecimal receivedKg, BigDecimal acceptedKg, BigDecimal rejectedKg, BigDecimal returnedKg,
                      BigDecimal orderedValue, BigDecimal payableValue) {
    }

    public record Totals(long purchases, BigDecimal orderedKg, BigDecimal receivedKg, BigDecimal acceptedKg,
                         BigDecimal rejectedKg, BigDecimal returnedKg, BigDecimal orderedValue,
                         BigDecimal payableValue) {
    }
}

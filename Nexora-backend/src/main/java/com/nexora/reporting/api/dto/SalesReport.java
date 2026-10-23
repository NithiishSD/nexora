package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyRef;
import com.nexora.reporting.application.SalesGrouping;

// GET /api/v1/reports/sales (E-RPT-06). Revenue is recognised at delivery only (FR-RPT-07): loans, transfers, chit
// payouts, opening balances and advances never appear here. No profit figures in the first release (FR-RPT-17).
// Per row only the grouping field is set: period (yyyy-MM), customer or productType.
public record SalesReport(LocalDate from, LocalDate to, SalesGrouping groupBy, List<Row> rows, Totals totals) {

    // orderValuePlaced and outstanding: items of orders dated in the period (outstanding = what is still owed on
    // them now); revenueDelivered and kgDelivered: items of orders delivered in the period.
    public record Row(String period, PartyRef customer, ProductType productType, BigDecimal orderValuePlaced,
                      BigDecimal revenueDelivered, BigDecimal kgDelivered, BigDecimal outstanding) {
    }

    public record Totals(BigDecimal orderValuePlaced, BigDecimal revenueDelivered, BigDecimal kgDelivered,
                         BigDecimal outstanding) {
    }
}

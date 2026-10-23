package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.inventory.domain.ProductType;

// GET /api/v1/reports/production (E-RPT-10): completed batches only; wastagePercent = wastage / input x 100.
public record ProductionReport(LocalDate from, LocalDate to, List<Row> rows, Totals totals) {

    public record Row(String batchNumber, ProductType productType, LocalDate completedOn, BigDecimal inputKg,
                      BigDecimal outputKg, BigDecimal wastageKg, BigDecimal discrepancyKg, BigDecimal wastagePercent) {
    }

    public record Totals(long batches, BigDecimal inputKg, BigDecimal outputKg, BigDecimal wastageKg,
                         BigDecimal discrepancyKg, BigDecimal wastagePercent) {
    }
}

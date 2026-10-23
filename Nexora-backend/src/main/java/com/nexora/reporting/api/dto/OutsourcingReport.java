package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyRef;

// GET /api/v1/reports/outsourcing (E-RPT-11): jobs issued in the period per manufacturer. outsideKg = issued -
// received - wastage; payable = received x the job's historical rate; paid = allocations of active payments.
public record OutsourcingReport(LocalDate from, LocalDate to, List<ManufacturerGroup> manufacturers, Totals totals) {

    public record ManufacturerGroup(PartyRef manufacturer, List<Job> jobs, Totals totals) {
    }

    public record Job(UUID jobId, String jobNumber, ProductType productType, LocalDate issueDate, String status,
                      BigDecimal issuedKg, BigDecimal receivedKg, BigDecimal wastageKg, BigDecimal outsideKg,
                      BigDecimal payable, BigDecimal paid, BigDecimal pending) {
    }

    public record Totals(BigDecimal issuedKg, BigDecimal receivedKg, BigDecimal wastageKg, BigDecimal outsideKg,
                         BigDecimal payable, BigDecimal paid, BigDecimal pending) {
    }
}

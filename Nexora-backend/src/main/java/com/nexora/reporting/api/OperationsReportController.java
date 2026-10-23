package com.nexora.reporting.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.reporting.api.dto.InventoryMovementReportLine;
import com.nexora.reporting.api.dto.OutsourcingReport;
import com.nexora.reporting.api.dto.ProductionReport;
import com.nexora.reporting.application.OperationsReportQuery;

// Stock movements (E-RPT-08), production and wastage (E-RPT-10) and outsourcing (E-RPT-11).
@RestController
class OperationsReportController {

    private final OperationsReportQuery reports;

    OperationsReportController(OperationsReportQuery reports) {
        this.reports = reports;
    }

    @GetMapping("/api/v1/reports/inventory-movements")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    List<InventoryMovementReportLine> inventoryMovements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam ProductType productType, @RequestParam(required = false) StockLocation location) {
        return reports.inventoryMovements(from, to, productType, location);
    }

    @GetMapping("/api/v1/reports/production")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    ProductionReport production(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required = false) ProductType productType) {
        return reports.production(from, to, productType);
    }

    @GetMapping("/api/v1/reports/outsourcing")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    OutsourcingReport outsourcing(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) UUID manufacturerId) {
        return reports.outsourcing(from, to, manufacturerId);
    }
}

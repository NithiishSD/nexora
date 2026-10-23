package com.nexora.reporting.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.inventory.domain.ProductType;
import com.nexora.reporting.api.dto.PurchaseReport;
import com.nexora.reporting.api.dto.SupplierPayablesReport;
import com.nexora.reporting.application.SupplierReportQuery;

// Supplier payables by age (E-RPT-04) and purchases per supplier and product (E-RPT-05).
@RestController
class SupplierReportController {

    private final SupplierReportQuery reports;

    SupplierReportController(SupplierReportQuery reports) {
        this.reports = reports;
    }

    @GetMapping("/api/v1/reports/supplier-payables")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    SupplierPayablesReport supplierPayables(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID supplierId) {
        return reports.payablesAgeing(from, to, supplierId);
    }

    @GetMapping("/api/v1/reports/purchases")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    PurchaseReport purchases(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(required = false) UUID supplierId,
                             @RequestParam(required = false) ProductType productType) {
        return reports.purchases(from, to, supplierId, productType);
    }
}

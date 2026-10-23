package com.nexora.reporting.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.inventory.domain.ProductType;
import com.nexora.reporting.api.dto.SalesReport;
import com.nexora.reporting.application.SalesGrouping;
import com.nexora.reporting.application.SalesReportQuery;

// Orders placed and revenue delivered (E-RPT-06).
@RestController
class SalesReportController {

    private final SalesReportQuery reports;

    SalesReportController(SalesReportQuery reports) {
        this.reports = reports;
    }

    @GetMapping("/api/v1/reports/sales")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    SalesReport sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(required = false) UUID customerId,
                      @RequestParam(required = false) ProductType productType,
                      @RequestParam(defaultValue = "MONTH") SalesGrouping groupBy) {
        return reports.report(from, to, customerId, productType, groupBy);
    }
}

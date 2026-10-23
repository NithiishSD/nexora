package com.nexora.reporting.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.reporting.api.dto.ExpenseReport;
import com.nexora.reporting.application.ExpenseReportQuery;

// Business spending, personal drawings and interest cost (E-RPT-07).
@RestController
class ExpenseReportController {

    private final ExpenseReportQuery reports;

    ExpenseReportController(ExpenseReportQuery reports) {
        this.reports = reports;
    }

    @GetMapping("/api/v1/reports/expenses")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    ExpenseReport expenses(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.report(from, to);
    }
}

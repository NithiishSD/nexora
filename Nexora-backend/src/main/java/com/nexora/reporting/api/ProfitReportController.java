package com.nexora.reporting.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.reporting.api.dto.ProfitAndLossReportLine;
import com.nexora.reporting.api.dto.ProfitabilityReportLine;
import com.nexora.reporting.application.PeriodGrouping;
import com.nexora.reporting.application.ProfitReportQuery;
import com.nexora.reporting.application.ProfitabilityDimension;

// Profit and Loss (E-RPT-15) and Profitability (E-RPT-16) reports.
@RestController
class ProfitReportController {

    private final ProfitReportQuery reports;

    ProfitReportController(ProfitReportQuery reports) {
        this.reports = reports;
    }

    @GetMapping("/api/v1/reports/profit-and-loss")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    List<ProfitAndLossReportLine> profitAndLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "MONTH") PeriodGrouping groupBy) {
        return reports.profitAndLoss(from, to, groupBy);
    }

    @GetMapping("/api/v1/reports/profitability")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    List<ProfitabilityReportLine> profitability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "PRODUCT") ProfitabilityDimension dimension) {
        return reports.profitability(from, to, dimension);
    }
}

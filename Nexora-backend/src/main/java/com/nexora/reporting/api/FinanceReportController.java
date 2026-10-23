package com.nexora.reporting.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.reporting.api.dto.CashBankReportLine;
import com.nexora.reporting.api.dto.LoansAndChitsReport;
import com.nexora.reporting.api.dto.WorkerEfficiencyRow;
import com.nexora.reporting.api.dto.WorkerPayableReportLine;
import com.nexora.reporting.application.FinanceReportQuery;

// Cash and bank (E-RPT-12), worker payable (E-RPT-13), loans and chits (E-RPT-14).
@RestController
class FinanceReportController {

    private final FinanceReportQuery reports;

    FinanceReportController(FinanceReportQuery reports) {
        this.reports = reports;
    }

    @GetMapping("/api/v1/reports/cash-bank")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    List<CashBankReportLine> cashBank(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) UUID accountId) {
        return reports.cashBank(from, to, accountId);
    }

    @GetMapping("/api/v1/reports/worker-payable")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    List<WorkerPayableReportLine> workerPayable(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID workerId) {
        return reports.workerPayable(from, to, workerId);
    }

    @GetMapping("/api/v1/reports/loans-and-chits")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    LoansAndChitsReport loansAndChits() {
        return reports.loansAndChits();
    }

    @GetMapping("/api/v1/reports/worker-efficiency")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    List<WorkerEfficiencyRow> workerEfficiency(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.workerEfficiency(from, to);
    }
}

package com.nexora.reporting.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.reporting.api.dto.CustomerOutstandingReport;
import com.nexora.reporting.api.dto.CustomerStatementResponse;
import com.nexora.reporting.application.CustomerReportQuery;

// Who owes what (E-RPT-02) and one customer's account over a period (E-RPT-03).
@RestController
class CustomerReportController {

    private final CustomerReportQuery reports;

    CustomerReportController(CustomerReportQuery reports) {
        this.reports = reports;
    }

    @GetMapping("/api/v1/reports/customer-outstanding")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    CustomerOutstandingReport outstanding(@RequestParam(required = false) BigDecimal minOutstanding,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                                          @PageableDefault(sort = "outstanding", direction = Sort.Direction.DESC)
                                          Pageable pageable) {
        return reports.outstanding(minOutstanding, asOf, pageable);
    }

    @GetMapping("/api/v1/customers/{id}/statement")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    CustomerStatementResponse statement(@PathVariable UUID id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.statement(id, from, to);
    }
}

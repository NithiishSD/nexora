package com.nexora.workforce.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.workforce.api.dto.WorkerPayableResponse;
import com.nexora.workforce.application.WorkerPayableQuery;

// What is owed to a worker for recorded work. Payments themselves go through POST /api/v1/payments.
@RestController
class WorkerPayableController {

    private final WorkerPayableQuery payables;

    WorkerPayableController(WorkerPayableQuery payables) {
        this.payables = payables;
    }

    // GET /api/v1/workers/{id}/payable?from=&to= (a payroll week, or everything)
    @GetMapping("/api/v1/workers/{id}/payable")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    WorkerPayableResponse payable(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return payables.forWorker(id, from, to);
    }
}

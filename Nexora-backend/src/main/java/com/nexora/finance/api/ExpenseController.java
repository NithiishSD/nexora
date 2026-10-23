package com.nexora.finance.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.finance.api.dto.ExpenseResponse;
import com.nexora.finance.api.dto.ExpenseSummaryResponse;
import com.nexora.finance.api.dto.RecordExpenseRequest;
import com.nexora.finance.api.dto.ReverseExpenseRequest;
import com.nexora.finance.application.ExpenseService;
import com.nexora.finance.domain.ExpenseCategory;
import com.nexora.finance.domain.ExpenseKind;
import com.nexora.finance.domain.RecordStatus;
import com.nexora.finance.domain.Recurrence;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.idempotency.Idempotent;

import jakarta.validation.Valid;

// Expenses and personal drawings. No PUT, PATCH or DELETE (405): a mistake is reversed (FR-FIN-14).
@RestController
@RequestMapping("/api/v1/expenses")
class ExpenseController {

    private final ExpenseService expenses;

    ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    @Idempotent
    ResponseEntity<ExpenseResponse> record(@Valid @RequestBody RecordExpenseRequest request) {
        ExpenseResponse created = expenses.record(request);
        return ResponseEntity.created(URI.create("/api/v1/expenses/" + created.id())).body(created);
    }

    // GET /api/v1/expenses?category=&expenseKind=&recurrence=&accountId=&status=&from=&to=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    PageResponse<ExpenseResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ExpenseKind expenseKind,
            @RequestParam(required = false) Recurrence recurrence,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "expenseDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return expenses.list(category, expenseKind, recurrence, accountId, status, from, to, pageable);
    }

    // GET /api/v1/expenses/summary?from=&to= (both required)
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    ExpenseSummaryResponse summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return expenses.summary(from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    ExpenseResponse get(@PathVariable UUID id) {
        return expenses.get(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    @Idempotent
    ExpenseResponse reverse(@PathVariable UUID id, @Valid @RequestBody ReverseExpenseRequest request) {
        return expenses.reverse(id, request.reason());
    }
}

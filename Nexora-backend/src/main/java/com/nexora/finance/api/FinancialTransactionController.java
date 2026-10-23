package com.nexora.finance.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.finance.api.dto.TransactionResponse;
import com.nexora.finance.application.FinancialAccountService;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.shared.api.PageResponse;

// The money ledger, read only (FR-FIN-04): there is no POST, PUT, PATCH or DELETE here, so every write is 405.
@RestController
@RequestMapping("/api/v1/finance/transactions")
class FinancialTransactionController {

    private final FinancialAccountService accountService;

    FinancialTransactionController(FinancialAccountService accountService) {
        this.accountService = accountService;
    }

    // GET /api/v1/finance/transactions?accountId=&type=&direction=&from=&to=&referenceType=&referenceId=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    PageResponse<TransactionResponse> list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Direction direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionReferenceType referenceType,
            @RequestParam(required = false) UUID referenceId,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return accountService.transactions(accountId, type, direction, from, to, referenceType, referenceId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    TransactionResponse get(@PathVariable UUID id) {
        return accountService.transaction(id);
    }
}

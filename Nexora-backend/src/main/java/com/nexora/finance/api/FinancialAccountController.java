package com.nexora.finance.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
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

import com.nexora.finance.api.dto.AccountAdjustmentRequest;
import com.nexora.finance.api.dto.AccountLineResponse;
import com.nexora.finance.api.dto.AccountListResponse;
import com.nexora.finance.api.dto.AccountResponse;
import com.nexora.finance.api.dto.CreateAccountRequest;
import com.nexora.finance.api.dto.TransactionResponse;
import com.nexora.finance.application.FinancialAccountService;
import com.nexora.finance.domain.AccountStatus;
import com.nexora.finance.domain.AccountType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.idempotency.Idempotent;

import jakarta.validation.Valid;

// Cash and bank accounts. No PUT, PATCH or DELETE: an account is closed, never deleted (405).
@RestController
@RequestMapping("/api/v1/finance/accounts")
class FinancialAccountController {

    private final FinancialAccountService accountService;

    FinancialAccountController(FinancialAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse created = accountService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/finance/accounts/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    AccountListResponse list(@RequestParam(required = false) AccountStatus status,
                             @RequestParam(required = false) AccountType accountType) {
        return accountService.list(status, accountType);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    AccountResponse get(@PathVariable UUID id) {
        return accountService.get(id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    AccountResponse close(@PathVariable UUID id) {
        return accountService.close(id);
    }

    @PostMapping("/{id}/adjustments")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @Idempotent
    ResponseEntity<TransactionResponse> adjust(@PathVariable UUID id,
                                               @Valid @RequestBody AccountAdjustmentRequest request) {
        TransactionResponse created = accountService.adjust(id, request);
        return ResponseEntity.created(URI.create("/api/v1/finance/transactions/" + created.id())).body(created);
    }

    // GET /api/v1/finance/accounts/{id}/transactions?type=&from=&to=&page=&size= (oldest first, fixed)
    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    PageResponse<AccountLineResponse> history(
            @PathVariable UUID id,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50) Pageable pageable) {
        return accountService.history(id, type, from, to, pageable);
    }
}

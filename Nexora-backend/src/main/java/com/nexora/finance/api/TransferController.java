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

import com.nexora.finance.api.dto.TransferRequest;
import com.nexora.finance.api.dto.TransferResponse;
import com.nexora.finance.application.TransferService;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.idempotency.Idempotent;

import jakarta.validation.Valid;

// Transfers between own accounts. No PUT, PATCH or DELETE (405): a mistake is a transfer the other way.
@RestController
@RequestMapping("/api/v1/finance/transfers")
class TransferController {

    private final TransferService transfers;

    TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @Idempotent
    ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse created = transfers.transfer(request);
        return ResponseEntity.created(URI.create("/api/v1/finance/transfers/" + created.id())).body(created);
    }

    // GET /api/v1/finance/transfers?accountId=&from=&to=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    PageResponse<TransferResponse> list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "transferDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return transfers.list(accountId, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    TransferResponse get(@PathVariable UUID id) {
        return transfers.get(id);
    }
}

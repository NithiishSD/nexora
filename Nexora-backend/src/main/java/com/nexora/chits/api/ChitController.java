package com.nexora.chits.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.chits.api.dto.ChitBidRequest;
import com.nexora.chits.api.dto.ChitBidResponse;
import com.nexora.chits.api.dto.ChitEntryRequest;
import com.nexora.chits.api.dto.ChitResponse;
import com.nexora.chits.api.dto.CreateChitRequest;
import com.nexora.chits.api.dto.ReverseChitEntryRequest;
import com.nexora.chits.application.ChitService;
import com.nexora.chits.domain.ChitStatus;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.idempotency.Idempotent;

import jakarta.validation.Valid;

// Chits. No PUT, PATCH or DELETE (405): an entry is corrected by reversing it.
@RestController
@RequestMapping("/api/v1/chits")
class ChitController {

    private final ChitService chits;

    ChitController(ChitService chits) {
        this.chits = chits;
    }

    // No key: no money moves (prior contributions are counted, not paid).
    @PostMapping
    @PreAuthorize("hasAuthority('CHIT_MANAGE')")
    ResponseEntity<ChitResponse> create(@Valid @RequestBody CreateChitRequest request) {
        return created(chits.create(request));
    }

    // GET /api/v1/chits?status=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('CHIT_MANAGE')")
    PageResponse<ChitResponse> list(@RequestParam(required = false) ChitStatus status,
                                    @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
                                    Pageable pageable) {
        return chits.list(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CHIT_MANAGE')")
    ChitResponse get(@PathVariable UUID id) {
        return chits.get(id);
    }

    @PostMapping("/{id}/contributions")
    @PreAuthorize("hasAuthority('CHIT_MANAGE')")
    @Idempotent
    ResponseEntity<ChitResponse> contribute(@PathVariable UUID id, @Valid @RequestBody ChitEntryRequest request) {
        return created(chits.contribute(id, request));
    }

    @PostMapping("/{id}/payout")
    @PreAuthorize("hasAuthority('CHIT_MANAGE')")
    @Idempotent
    ResponseEntity<ChitResponse> payout(@PathVariable UUID id, @Valid @RequestBody ChitEntryRequest request) {
        return created(chits.payout(id, request));
    }

    @PostMapping("/{id}/organiser-payments")
    @PreAuthorize("hasAuthority('CHIT_MANAGE')")
    @Idempotent
    ResponseEntity<ChitResponse> payOrganiser(@PathVariable UUID id, @Valid @RequestBody ChitEntryRequest request) {
        return created(chits.payOrganiser(id, request));
    }

    @PostMapping("/{id}/entries/{transactionId}/reverse")
    @PreAuthorize("hasAuthority('CHIT_MANAGE')")
    @Idempotent
    ChitResponse reverseEntry(@PathVariable UUID id, @PathVariable UUID transactionId,
                              @Valid @RequestBody ReverseChitEntryRequest request) {
        return chits.reverseEntry(id, transactionId, request.reason());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('CHIT_MANAGE')")
    ChitResponse close(@PathVariable UUID id) {
        return chits.close(id);
    }

    // E-CHIT-09. Monthly bid and dividend calculation (FR-CHIT-10).
    @PostMapping("/{id}/bids")
    @PreAuthorize("hasAuthority('CHIT_MANAGE')")
    ChitBidResponse calculateBid(@PathVariable UUID id, @Valid @RequestBody ChitBidRequest request) {
        return chits.calculateBid(id, request);
    }

    private static ResponseEntity<ChitResponse> created(ChitResponse chit) {
        return ResponseEntity.created(URI.create("/api/v1/chits/" + chit.id())).body(chit);
    }
}

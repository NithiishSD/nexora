package com.nexora.finance.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.finance.api.dto.OpeningObligationRequest;
import com.nexora.finance.api.dto.OpeningObligationResponse;
import com.nexora.finance.application.OpeningObligationService;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.shared.api.PageResponse;

import jakarta.validation.Valid;

// Opening receivables and payables (E-PAY-11/12). Entered once at go-live and never edited (no PUT/PATCH/DELETE).
@RestController
@RequestMapping("/api/v1/opening-obligations")
class OpeningObligationController {

    private final OpeningObligationService openingService;

    OpeningObligationController(OpeningObligationService openingService) {
        this.openingService = openingService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    ResponseEntity<OpeningObligationResponse> create(@Valid @RequestBody OpeningObligationRequest request) {
        OpeningObligationResponse created = openingService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/opening-obligations/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    PageResponse<OpeningObligationResponse> list(
            @RequestParam(required = false) UUID partyId,
            @RequestParam(required = false) PaymentPurpose purpose,
            @RequestParam(required = false) Boolean outstandingOnly,
            @PageableDefault(size = 20, sort = "obligationDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return openingService.list(partyId, purpose, outstandingOnly, pageable);
    }
}

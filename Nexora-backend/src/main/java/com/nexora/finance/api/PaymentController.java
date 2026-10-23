package com.nexora.finance.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
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

import com.nexora.finance.api.dto.AllocationResponse;
import com.nexora.finance.api.dto.PaymentResponse;
import com.nexora.finance.api.dto.PaymentSummaryResponse;
import com.nexora.finance.api.dto.RecordPaymentRequest;
import com.nexora.finance.api.dto.ReversePaymentRequest;
import com.nexora.finance.api.dto.ChequeActionRequest;
import com.nexora.finance.api.dto.RefundPaymentRequest;
import com.nexora.finance.application.ChequeService;
import com.nexora.finance.application.PaymentReversalService;
import com.nexora.finance.application.PaymentService;
import com.nexora.finance.domain.PaymentDirection;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.finance.domain.RecordStatus;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.idempotency.Idempotent;

import jakarta.validation.Valid;

// Payments are history: no PUT, PATCH or DELETE (405, FR-PAY-16); a mistake is reversed.
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {

    private final PaymentService paymentService;
    private final PaymentReversalService reversalService;
    private final ChequeService chequeService;

    PaymentController(PaymentService paymentService, PaymentReversalService reversalService, ChequeService chequeService) {
        this.paymentService = paymentService;
        this.reversalService = reversalService;
        this.chequeService = chequeService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    @Idempotent
    ResponseEntity<PaymentResponse> record(@Valid @RequestBody RecordPaymentRequest request) {
        PaymentResponse created = paymentService.record(request);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + created.id())).body(created);
    }

    // GET /api/v1/payments?partyId=&purpose=&direction=&method=&accountId=&status=&from=&to=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    PageResponse<PaymentSummaryResponse> list(
            @RequestParam(required = false) UUID partyId,
            @RequestParam(required = false) PaymentPurpose purpose,
            @RequestParam(required = false) PaymentDirection direction,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.list(partyId, purpose, direction, method, accountId, status, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    PaymentResponse get(@PathVariable UUID id) {
        return paymentService.get(id);
    }

    @GetMapping("/{id}/allocations")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    List<AllocationResponse> allocations(@PathVariable UUID id) {
        return paymentService.allocations(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('PAYMENT_REVERSE')")
    @Idempotent
    PaymentResponse reverse(@PathVariable UUID id, @Valid @RequestBody ReversePaymentRequest request) {
        return reversalService.reverse(id, request.reason());
    }

    // E-PAY-13: Refund unallocated advance money back to customer
    @PostMapping("/{id}/refunds")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    @Idempotent
    ResponseEntity<PaymentResponse> refund(@PathVariable UUID id, @Valid @RequestBody RefundPaymentRequest request) {
        PaymentResponse created = paymentService.refund(id, request);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + created.id())).body(created);
    }

    // E-PAY-14: Clear a pending cheque
    @PostMapping("/{id}/cheque-clearance")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    PaymentResponse clearCheque(@PathVariable UUID id, @RequestBody(required = false) ChequeActionRequest request) {
        LocalDate date = request != null ? request.date() : null;
        String notes = request != null ? request.notes() : null;
        return chequeService.clear(id, date, notes);
    }

    // E-PAY-14: Mark a cheque as bounced
    @PostMapping("/{id}/cheque-bounce")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    PaymentResponse bounceCheque(@PathVariable UUID id, @RequestBody(required = false) ChequeActionRequest request) {
        LocalDate date = request != null ? request.date() : null;
        String notes = request != null ? request.notes() : null;
        return chequeService.bounce(id, date, notes);
    }
}

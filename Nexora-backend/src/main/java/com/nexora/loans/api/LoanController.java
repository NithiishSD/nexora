package com.nexora.loans.api;

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

import com.nexora.loans.api.dto.CreateLoanRequest;
import com.nexora.loans.api.dto.ExistingLoanRequest;
import com.nexora.loans.api.dto.LoanAdviceResponse;
import com.nexora.loans.api.dto.LoanPaymentRequest;
import com.nexora.loans.api.dto.LoanResponse;
import com.nexora.loans.api.dto.ReverseLoanPaymentRequest;
import com.nexora.loans.application.LoanService;
import com.nexora.loans.domain.LoanStatus;
import com.nexora.loans.domain.LoanType;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.idempotency.Idempotent;

import jakarta.validation.Valid;

// Loans. No PUT, PATCH or DELETE (405, FR-LOAN-09): money is corrected by reversing a payment.
@RestController
@RequestMapping("/api/v1/loans")
class LoanController {

    private final LoanService loans;

    LoanController(LoanService loans) {
        this.loans = loans;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    @Idempotent
    ResponseEntity<LoanResponse> create(@Valid @RequestBody CreateLoanRequest request) {
        return created(loans.create(request));
    }

    // No key: no money moves (E-LOAN-02).
    @PostMapping("/existing")
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    ResponseEntity<LoanResponse> registerExisting(@Valid @RequestBody ExistingLoanRequest request) {
        return created(loans.registerExisting(request));
    }

    // GET /api/v1/loans?status=&lenderId=&loanType=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    PageResponse<LoanResponse> list(@RequestParam(required = false) LoanStatus status,
                                    @RequestParam(required = false) UUID lenderId,
                                    @RequestParam(required = false) LoanType loanType,
                                    @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
                                    Pageable pageable) {
        return loans.list(status, lenderId, loanType, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    LoanResponse get(@PathVariable UUID id) {
        return loans.get(id);
    }

    // E-LOAN-09. Interest calculation and repayment advice (FR-LOAN-10).
    @GetMapping("/{id}/advice")
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    LoanAdviceResponse getAdvice(@PathVariable UUID id) {
        return loans.getAdvice(id);
    }

    @PostMapping("/{id}/principal-repayments")
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    @Idempotent
    ResponseEntity<LoanResponse> repayPrincipal(@PathVariable UUID id, @Valid @RequestBody LoanPaymentRequest request) {
        return created(loans.repayPrincipal(id, request));
    }

    @PostMapping("/{id}/interest-payments")
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    @Idempotent
    ResponseEntity<LoanResponse> payInterest(@PathVariable UUID id, @Valid @RequestBody LoanPaymentRequest request) {
        return created(loans.payInterest(id, request));
    }

    @PostMapping("/{id}/payments/{transactionId}/reverse")
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    @Idempotent
    LoanResponse reversePayment(@PathVariable UUID id, @PathVariable UUID transactionId,
                                @Valid @RequestBody ReverseLoanPaymentRequest request) {
        return loans.reversePayment(id, transactionId, request.reason());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    LoanResponse close(@PathVariable UUID id) {
        return loans.close(id);
    }

    private static ResponseEntity<LoanResponse> created(LoanResponse loan) {
        return ResponseEntity.created(URI.create("/api/v1/loans/" + loan.id())).body(loan);
    }
}

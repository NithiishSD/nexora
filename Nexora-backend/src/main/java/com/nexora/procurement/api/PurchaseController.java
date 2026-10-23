package com.nexora.procurement.api;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.inventory.domain.ProductType;
import com.nexora.procurement.api.dto.CancelPurchaseRequest;
import com.nexora.procurement.api.dto.CreatePurchaseRequest;
import com.nexora.procurement.api.dto.PurchaseResponse;
import com.nexora.procurement.api.dto.PurchaseSummaryResponse;
import com.nexora.procurement.api.dto.ReceiptResponse;
import com.nexora.procurement.api.dto.RecordReceiptRequest;
import com.nexora.procurement.api.dto.RecordReturnRequest;
import com.nexora.procurement.api.dto.SupplierReturnResponse;
import com.nexora.procurement.api.dto.UpdatePurchaseRequest;
import com.nexora.procurement.application.PurchaseReceivingService;
import com.nexora.procurement.application.PurchaseService;
import com.nexora.procurement.application.RecordedDocument;
import com.nexora.procurement.domain.PurchaseStatus;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.idempotency.Idempotent;
import com.nexora.shared.domain.PaymentStatus;

import jakarta.validation.Valid;

// HTTP only: validate, call one service method, choose the status. There is no DELETE (purchases are history,
// FR-PROC-18 -> 405) and no PUT; PATCH changes only notes and the bill reference.
@RestController
@RequestMapping("/api/v1/purchases")
class PurchaseController {

    private final PurchaseService purchaseService;
    private final PurchaseReceivingService receivingService;

    PurchaseController(PurchaseService purchaseService, PurchaseReceivingService receivingService) {
        this.purchaseService = purchaseService;
        this.receivingService = receivingService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    ResponseEntity<PurchaseResponse> create(@Valid @RequestBody CreatePurchaseRequest request) {
        PurchaseResponse created = purchaseService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/purchases/" + created.id())).body(created);
    }

    // GET /api/v1/purchases?supplierId=&status=&productType=&from=&to=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    PageResponse<PurchaseSummaryResponse> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) Boolean overdue,
            @PageableDefault(size = 20, sort = "purchaseDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return purchaseService.list(supplierId, status, productType, from, to, paymentStatus, overdue, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    PurchaseResponse get(@PathVariable UUID id) {
        return purchaseService.get(id);
    }

    // E-PROC-06: 201 + Location of the new receipt; the body is the purchase with its receipts.
    @PostMapping("/{id}/receipts")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    @Idempotent
    ResponseEntity<PurchaseResponse> receive(@PathVariable UUID id, @Valid @RequestBody RecordReceiptRequest request) {
        RecordedDocument receipt = receivingService.receive(id, request);
        return ResponseEntity.created(URI.create("/api/v1/purchases/" + id + "/receipts/" + receipt.id()))
                .body(receipt.purchase());
    }

    // E-PROC-08. A receipt is never edited or deleted (FR-PROC-14): PUT/PATCH/DELETE here are 405.
    @GetMapping("/{id}/receipts/{receiptId}")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    ReceiptResponse getReceipt(@PathVariable UUID id, @PathVariable UUID receiptId) {
        return purchaseService.getReceipt(id, receiptId);
    }

    // E-PROC-07: 201 + Location of the new return; the body is the purchase.
    @PostMapping("/{id}/returns")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    @Idempotent
    ResponseEntity<PurchaseResponse> recordReturn(@PathVariable UUID id, @Valid @RequestBody RecordReturnRequest request) {
        RecordedDocument supplierReturn = receivingService.recordReturn(id, request);
        return ResponseEntity.created(URI.create("/api/v1/purchases/" + id + "/returns/" + supplierReturn.id()))
                .body(supplierReturn.purchase());
    }

    // E-PROC-09
    @GetMapping("/{id}/returns/{returnId}")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    SupplierReturnResponse getReturn(@PathVariable UUID id, @PathVariable UUID returnId) {
        return purchaseService.getReturn(id, returnId);
    }

    // E-PROC-05: a business action, not a delete; the record stays.
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    PurchaseResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelPurchaseRequest request) {
        return receivingService.cancel(id, request.reason());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    PurchaseResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePurchaseRequest request) {
        return purchaseService.update(id, request);
    }
}

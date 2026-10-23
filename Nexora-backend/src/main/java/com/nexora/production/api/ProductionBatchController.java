package com.nexora.production.api;

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
import com.nexora.production.api.dto.BatchResponse;
import com.nexora.production.api.dto.CancelBatchRequest;
import com.nexora.production.api.dto.CompleteBatchRequest;
import com.nexora.production.api.dto.CreateBatchRequest;
import com.nexora.production.api.dto.StartBatchRequest;
import com.nexora.production.api.dto.UpdateBatchRequest;
import com.nexora.production.application.ProductionService;
import com.nexora.production.domain.BatchStatus;
import com.nexora.shared.api.PageResponse;

import jakarta.validation.Valid;

// Production batches. No PUT and no DELETE (405): a batch is history once planned (FR-PROD-10).
@RestController
@RequestMapping("/api/v1/production-batches")
class ProductionBatchController {

    private final ProductionService productionService;

    ProductionBatchController(ProductionService productionService) {
        this.productionService = productionService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCTION_MANAGE')")
    ResponseEntity<BatchResponse> create(@Valid @RequestBody CreateBatchRequest request) {
        BatchResponse created = productionService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/production-batches/" + created.id())).body(created);
    }

    // GET /api/v1/production-batches?status=&productType=&orderId=&from=&to=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    PageResponse<BatchResponse> list(
            @RequestParam(required = false) BatchStatus status,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "productionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return productionService.list(status, productType, orderId, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    BatchResponse get(@PathVariable UUID id) {
        return productionService.get(id);
    }

    // No idempotency key: a repeat is refused by the state machine (409), never applied twice.
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('PRODUCTION_MANAGE')")
    BatchResponse start(@PathVariable UUID id, @Valid @RequestBody(required = false) StartBatchRequest request) {
        return productionService.start(id, request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PRODUCTION_MANAGE')")
    BatchResponse complete(@PathVariable UUID id, @Valid @RequestBody CompleteBatchRequest request) {
        return productionService.complete(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PRODUCTION_MANAGE')")
    BatchResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelBatchRequest request) {
        return productionService.cancel(id, request.reason());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_MANAGE')")
    BatchResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBatchRequest request) {
        return productionService.update(id, request);
    }
}

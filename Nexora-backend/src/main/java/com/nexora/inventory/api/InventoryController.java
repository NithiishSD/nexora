package com.nexora.inventory.api;

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

import com.nexora.inventory.api.dto.ExternalWipResponse;
import com.nexora.inventory.api.dto.MovementResponse;
import com.nexora.inventory.api.dto.OpeningStockRequest;
import com.nexora.inventory.api.dto.RecordWastageRequest;
import com.nexora.inventory.api.dto.StockAdjustmentRequest;
import com.nexora.inventory.api.dto.StockSummaryResponse;
import com.nexora.inventory.application.InventoryAdjustmentService;
import com.nexora.inventory.application.InventoryQueryService;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.shared.api.PageResponse;

import jakarta.validation.Valid;

// Stock is read here and never written from outside (FR-INV-01): there is no POST/PUT/PATCH/DELETE on
// movements, so Spring answers those with 405 METHOD_NOT_ALLOWED. The only writes are the business actions
// (wastage, adjustment, opening stock) that go through InventoryService.
@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController {

    private final InventoryQueryService queries;
    private final InventoryAdjustmentService adjustments;

    InventoryController(InventoryQueryService queries, InventoryAdjustmentService adjustments) {
        this.queries = queries;
        this.adjustments = adjustments;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    StockSummaryResponse summary() {
        return queries.summary();
    }

    // GET /api/v1/inventory/movements?productType=&location=&movementType=&referenceType=&referenceId=&from=&to=
    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    PageResponse<MovementResponse> movements(
            @RequestParam(required = false) ProductType productType,      // unknown value -> 400 MALFORMED_REQUEST
            @RequestParam(required = false) StockLocation location,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) MovementReferenceType referenceType,
            @RequestParam(required = false) UUID referenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return queries.movements(productType, location, movementType, referenceType, referenceId, from, to, pageable);
    }

    @GetMapping("/movements/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    MovementResponse movement(@PathVariable UUID id) {
        return queries.movement(id);
    }

    // ---- business actions that change stock (owner only) ----------------------------------------------

    // GET /api/v1/inventory/external-wip?manufacturerId=
    @GetMapping("/external-wip")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    ExternalWipResponse externalWip(@RequestParam(required = false) UUID manufacturerId) {
        return queries.externalWip(manufacturerId);
    }

    @PostMapping("/wastage")
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    ResponseEntity<MovementResponse> recordWastage(@Valid @RequestBody RecordWastageRequest request) {
        return created(adjustments.recordWastage(request));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    ResponseEntity<MovementResponse> adjust(@Valid @RequestBody StockAdjustmentRequest request) {
        return created(adjustments.adjust(request));
    }

    @PostMapping("/opening-balances")
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    ResponseEntity<MovementResponse> recordOpeningStock(@Valid @RequestBody OpeningStockRequest request) {
        return created(adjustments.recordOpeningStock(request));
    }

    // 201 Created + Location of the new ledger row.
    private static ResponseEntity<MovementResponse> created(MovementResponse movement) {
        return ResponseEntity.created(URI.create("/api/v1/inventory/movements/" + movement.id())).body(movement);
    }
}

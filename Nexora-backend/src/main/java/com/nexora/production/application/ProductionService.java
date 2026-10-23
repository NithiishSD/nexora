package com.nexora.production.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.application.MovementCommand;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.orders.application.OrderService;
import com.nexora.production.api.dto.BatchLinkRequest;
import com.nexora.production.api.dto.BatchResponse;
import com.nexora.production.api.dto.CompleteBatchRequest;
import com.nexora.production.api.dto.CreateBatchRequest;
import com.nexora.production.api.dto.StartBatchRequest;
import com.nexora.production.api.dto.UpdateBatchRequest;
import com.nexora.production.domain.BatchStatus;
import com.nexora.production.domain.ProductionBatch;
import com.nexora.production.domain.ProductionOrderItem;
import com.nexora.production.infrastructure.ProductionBatchRepository;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.InvalidStateTransitionException;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// Production batch use cases: plan, view, list, edit, start, complete, cancel. One public method = one
// use case = one transaction.
@Service
public class ProductionService {

    // Reasons on the WASTAGE movements of a completion (05 E-PROD-06); the wastage report tells them apart.
    static final String PRODUCTION_WASTAGE = "PRODUCTION_WASTAGE";
    static final String PRODUCTION_DISCREPANCY = "PRODUCTION_DISCREPANCY";

    private static final Logger log = LoggerFactory.getLogger(ProductionService.class);
    private static final Set<String> SORTABLE = Set.of("productionDate", "batchNumber", "createdAt");

    private final ProductionBatchRepository batches;
    private final OrderService orders;
    private final BusinessNumberGenerator numbers;
    private final BatchViews views;
    private final InventoryService inventory;
    private final AuditService audit;
    private final Clock clock;

    public ProductionService(ProductionBatchRepository batches, OrderService orders, BusinessNumberGenerator numbers,
                             BatchViews views, InventoryService inventory, AuditService audit, Clock clock) {
        this.batches = batches;
        this.orders = orders;
        this.numbers = numbers;
        this.views = views;
        this.inventory = inventory;
        this.audit = audit;
        this.clock = clock;
    }

    // E-PROD-01. The BAT number is the last lock taken (ADR-028); no stock moves on a plan.
    @Transactional
    public BatchResponse create(CreateBatchRequest r) {
        List<ProductionOrderItem> links = links(r.productType(), r.orderItems());
        ProductionBatch batch = ProductionBatch.plan(numbers.next("BAT"), r.productType(), r.productionDate(),
                r.inputWeightKg(), blankToNull(r.notes()), links);
        batches.save(batch);
        log.info("event=batch_planned number={} product={} inputKg={}", batch.getBatchNumber(), batch.getProductType(),
                batch.getInputWeightKg());
        return views.of(batch);
    }

    @Transactional(readOnly = true)
    public BatchResponse get(UUID id) {
        return views.of(find(id));
    }

    // E-PROD-02. status=IN_PROGRESS is the internal WIP list.
    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> list(BatchStatus status, ProductType productType, UUID orderId, LocalDate from,
                                            LocalDate to, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        ListParameters.requireDateRange(from, to);
        List<UUID> orderItems = orderId == null ? null : orders.itemIdsOf(orderId);
        Specification<ProductionBatch> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<ProductionBatch> part : Arrays.<Specification<ProductionBatch>>asList(
                status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status),
                productType == null ? null : (root, query, cb) -> cb.equal(root.get("productType"), productType),
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("productionDate"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("productionDate"), to),
                orderItems == null ? null : (root, query, cb) -> {
                    query.distinct(true);
                    return orderItems.isEmpty() ? cb.disjunction()
                            : root.join("orderItems").get("orderItemId").in(orderItems);
                })) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        return views.page(batches.findAll(filter, pageable));
    }

    // E-PROD-04: only while PLANNED (409 otherwise); the row is locked so a parallel start cannot slip in between.
    @Transactional
    public BatchResponse update(UUID id, UpdateBatchRequest r) {
        ProductionBatch batch = lock(id);
        if (batch.getStatus() != BatchStatus.PLANNED) {                // before the links are checked: 409 first
            throw new InvalidStateTransitionException("Batch " + batch.getBatchNumber(), batch.getStatus(), "EDITED");
        }
        batch.edit(r.productionDate() != null ? r.productionDate() : batch.getProductionDate(),
                r.inputWeightKg() != null ? r.inputWeightKg() : batch.getInputWeightKg(),
                r.notes() == null ? batch.getNotes() : blankToNull(r.notes()),
                r.orderItems() == null ? batch.getOrderItems() : links(batch.getProductType(), r.orderItems()));
        log.info("event=batch_updated number={}", batch.getBatchNumber());
        return views.of(batches.saveAndFlush(batch));
    }

    // E-PROD-05. Lock order (ADR-028): batch row -> linked order rows (ascending id) -> raw-silk and WIP balances ->
    // MOV number. All or nothing (FR-PROD-08): not enough raw silk -> 409 INSUFFICIENT_INVENTORY, the batch stays
    // PLANNED and no order is locked; a linked order cancelled meanwhile -> 409 INVALID_STATE_TRANSITION.
    @Transactional
    public BatchResponse start(UUID id, StartBatchRequest r) {
        ProductionBatch batch = lock(id);
        batch.start(clock.instant());                                     // 409 unless PLANNED
        orders.lockForProduction(batch.getOrderItems().stream().map(ProductionOrderItem::getOrderItemId).toList());
        LocalDate date = r != null && r.startDate() != null ? r.startDate() : LocalDate.now(clock);
        // raw silk leaves RAW_STOCK and arrives in INTERNAL_WIP as the warp being made (plan decision R3)
        inventory.recordMovement(new MovementCommand(ProductType.RAW_SILK, batch.getProductType(),
                MovementType.CONSUMPTION, StockLocation.RAW_STOCK, StockLocation.INTERNAL_WIP, batch.getInputWeightKg(),
                date, MovementReferenceType.PRODUCTION_BATCH, batch.getId(), batch.getBatchNumber(), null, null));
        log.info("event=batch_started number={} inputKg={}", batch.getBatchNumber(), batch.getInputWeightKg());
        return views.of(batches.saveAndFlush(batch));
    }

    // E-PROD-06. Lock order (ADR-028): batch row -> INTERNAL_WIP and FINISHED_STOCK balances -> MOV numbers. The whole
    // input leaves INTERNAL_WIP: output to FINISHED_STOCK, wastage and discrepancy to WASTAGE, the discrepancy flagged by
    // its reason so it stays visible (FR-PROD-04); zero quantities move nothing. 422 PRODUCTION_NOT_RECONCILED before
    // any stock is touched.
    @Transactional
    public BatchResponse complete(UUID id, CompleteBatchRequest r) {
        ProductionBatch batch = lock(id);
        batch.complete(r.outputWeightKg(), r.wastageWeightKg(), r.discrepancyWeightKg(),
                r.notes() == null ? batch.getNotes() : blankToNull(r.notes()), clock.instant());
        LocalDate date = r.completionDate() != null ? r.completionDate() : LocalDate.now(clock);
        List<MovementCommand> moves = new ArrayList<>();
        addOut(moves, batch, MovementType.PRODUCTION_OUTPUT, StockLocation.FINISHED_STOCK, batch.getOutputWeightKg(),
                null, date);
        addOut(moves, batch, MovementType.WASTAGE, StockLocation.WASTAGE, batch.getWastageWeightKg(),
                PRODUCTION_WASTAGE, date);
        addOut(moves, batch, MovementType.WASTAGE, StockLocation.WASTAGE, batch.getDiscrepancyWeightKg(),
                PRODUCTION_DISCREPANCY, date);
        inventory.recordMovements(moves);
        log.info("event=batch_completed number={} inputKg={} outputKg={} wastageKg={} discrepancyKg={}",
                batch.getBatchNumber(), batch.getInputWeightKg(), batch.getOutputWeightKg(),
                batch.getWastageWeightKg(), batch.getDiscrepancyWeightKg());
        return views.of(batches.saveAndFlush(batch));
    }

    // E-PROD-07: only from PLANNED (nothing was consumed); a started batch is finished by completing it.
    @Transactional
    public BatchResponse cancel(UUID id, String reason) {
        ProductionBatch batch = lock(id);
        batch.cancel();
        audit.record(AuditAction.PRODUCTION_CANCELLED, "PRODUCTION_BATCH", batch.getId(), reason,
                Map.of("batchNumber", batch.getBatchNumber(), "inputWeightKg", batch.getInputWeightKg().toPlainString()));
        log.info("event=batch_cancelled number={} reason={}", batch.getBatchNumber(), reason);
        return views.of(batches.saveAndFlush(batch));
    }

    // For the workforce module: number of each known batch (unknown ids absent, the caller decides 404).
    @Transactional(readOnly = true)
    public Map<UUID, String> batchNumbers(Collection<UUID> ids) {
        return batches.findAllById(ids).stream()
                .collect(Collectors.toMap(ProductionBatch::getId, ProductionBatch::getBatchNumber));
    }

    // ---- helpers --------------------------------------------------------------------------

    // Links checked by orders (400 twice, 404 unknown, 422 other warp type, 409 order not workable).
    private List<ProductionOrderItem> links(ProductType productType, List<BatchLinkRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        orders.checkWorkLinks(productType, requested.stream().map(BatchLinkRequest::orderItemId).toList(), "batch");
        return requested.stream()
                .map(l -> new ProductionOrderItem(l.orderItemId(), Weight.round(l.allocatedWeightKg()))).toList();
    }

    // One movement of the batch's warp out of INTERNAL_WIP, none for a zero quantity.
    private static void addOut(List<MovementCommand> moves, ProductionBatch batch, MovementType type, StockLocation to,
                               BigDecimal kg, String reason, LocalDate date) {
        if (kg.signum() > 0) {
            moves.add(new MovementCommand(batch.getProductType(), null, type, StockLocation.INTERNAL_WIP, to, kg, date,
                    MovementReferenceType.PRODUCTION_BATCH, batch.getId(), batch.getBatchNumber(), reason, null));
        }
    }

    ProductionBatch lock(UUID id) {
        return batches.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Production batch", id));
    }

    private ProductionBatch find(UUID id) {
        return batches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Production batch", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

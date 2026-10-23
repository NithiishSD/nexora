package com.nexora.production.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.nexora.inventory.application.InventoryQueryService;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.orders.application.OrderItemRef;
import com.nexora.orders.application.OrderService;
import com.nexora.production.api.dto.BatchResponse;
import com.nexora.production.domain.ProductionBatch;
import com.nexora.production.domain.ProductionOrderItem;
import com.nexora.production.infrastructure.BatchWorkRepository;
import com.nexora.shared.api.PageResponse;

// Builds BatchResponse: the aggregate plus the order numbers of its links (orders module), the number of its
// CONSUMPTION movement (inventory module) and, on the detail, its work records, each fetched with one query.
@Component
class BatchViews {

    private final OrderService orders;
    private final InventoryQueryService inventory;
    private final BatchWorkRepository work;

    BatchViews(OrderService orders, InventoryQueryService inventory, BatchWorkRepository work) {
        this.orders = orders;
        this.inventory = inventory;
        this.work = work;
    }

    // Detail: with its work records.
    BatchResponse of(ProductionBatch batch) {
        return rows(List.of(batch), work.workRecords(batch.getId())).getFirst();
    }

    // List rows: without work records (E-PROD-02).
    PageResponse<BatchResponse> page(Page<ProductionBatch> page) {
        Map<UUID, BatchResponse> rows = rows(page.getContent(), null).stream()
                .collect(Collectors.toMap(BatchResponse::id, r -> r));
        return PageResponse.from(page.map(b -> rows.get(b.getId())));
    }

    private List<BatchResponse> rows(List<ProductionBatch> batches, List<BatchResponse.WorkRecord> workRecords) {
        Map<UUID, OrderItemRef> refs = orders.itemRefs(batches.stream().flatMap(b -> b.getOrderItems().stream())
                .map(ProductionOrderItem::getOrderItemId).distinct().toList());
        Map<UUID, String> consumption = inventory.movementNumbersByReference(MovementReferenceType.PRODUCTION_BATCH,
                MovementType.CONSUMPTION, batches.stream().map(ProductionBatch::getId).toList());
        return batches.stream().map(b -> new BatchResponse(b.getId(), b.getBatchNumber(), b.getProductType(),
                b.getProductionDate(), b.getInputWeightKg(), b.getOutputWeightKg(), b.getWastageWeightKg(),
                b.getDiscrepancyWeightKg(), b.wastagePercent(), b.getStatus(), b.getStartedAt(), b.getCompletedAt(),
                b.getOrderItems().stream().map(l -> {
                    OrderItemRef ref = refs.get(l.getOrderItemId());
                    return new BatchResponse.Link(l.getOrderItemId(), ref.orderId(), ref.orderNumber(), ref.lineNo(),
                            l.getAllocatedWeightKg());
                }).toList(),
                workRecords, consumption.get(b.getId()), b.getNotes(), b.getCreatedAt(), b.getUpdatedAt(), b.getCreatedBy())).toList();
    }
}

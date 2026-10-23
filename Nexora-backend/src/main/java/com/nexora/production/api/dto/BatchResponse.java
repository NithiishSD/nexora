package com.nexora.production.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;
import com.nexora.production.domain.BatchStatus;

// One batch (05-API-SPEC section 13). Results are null until it is completed.
public record BatchResponse(
        UUID id,
        String batchNumber,
        ProductType productType,
        LocalDate productionDate,
        BigDecimal inputWeightKg,
        BigDecimal outputWeightKg,
        BigDecimal wastageWeightKg,
        BigDecimal discrepancyWeightKg,
        BigDecimal wastagePercent,
        BatchStatus status,
        Instant startedAt,
        Instant completedAt,
        List<Link> orderItems,
        List<WorkRecord> workRecords,          // detail only (null in lists), recorded work on this batch
        String consumptionMovementNumber,      // null until started
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {

    public record WorkRecord(UUID id, String workerName, String workType, BigDecimal amount) {
    }

    public record Link(UUID orderItemId, UUID orderId, String orderNumber, int lineNo, BigDecimal allocatedWeightKg) {
    }
}

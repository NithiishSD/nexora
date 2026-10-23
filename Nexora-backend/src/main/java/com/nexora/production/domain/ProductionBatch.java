package com.nexora.production.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.nexora.inventory.domain.ProductType;
import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.InvalidProductForOperationException;
import com.nexora.shared.error.InvalidStateTransitionException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

// An in-house production batch: raw silk in, silk or kora warp out (the aggregate root of production). Stock is
// moved by the service through InventoryService; this entity holds the plan, the state machine and the
// reconciliation rule input = output + wastage + discrepancy (DOMAIN_RULES section 4, Production).
@Entity
@Table(name = "production_batches")
public class ProductionBatch extends AuditableEntity {

    @Column(name = "batch_number", nullable = false, length = 30, updatable = false)
    private String batchNumber;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20, updatable = false)
    private ProductType productType;

    @Column(name = "input_weight_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal inputWeightKg;

    @Column(name = "output_weight_kg", precision = 12, scale = 3)
    private BigDecimal outputWeightKg;

    @Column(name = "wastage_weight_kg", precision = 12, scale = 3)
    private BigDecimal wastageWeightKg;

    @Column(name = "discrepancy_weight_kg", precision = 12, scale = 3)
    private BigDecimal discrepancyWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private BatchStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(columnDefinition = "text")
    private String notes;

    // Replacing the list deletes the old rows before inserting the new ones (element collection), so the
    // (batch, item) primary key never sees a duplicate during a flush.
    @ElementCollection
    @CollectionTable(name = "production_order_items", joinColumns = @JoinColumn(name = "production_batch_id"))
    @BatchSize(size = 100)
    private List<ProductionOrderItem> orderItems = new ArrayList<>();

    protected ProductionBatch() {
        // for JPA only
    }

    // FR-PROD-01: only silk or kora warp is produced; vuda warp is traded and raw silk is the input (FR-INV-10).
    public static ProductionBatch plan(String batchNumber, ProductType productType, LocalDate productionDate,
                                       BigDecimal inputWeightKg, String notes, List<ProductionOrderItem> links) {
        if (productType != ProductType.SILK_WARP && productType != ProductType.KORA_WARP) {
            throw new InvalidProductForOperationException(productType + " cannot be produced; a batch makes"
                    + " SILK_WARP or KORA_WARP.");
        }
        ProductionBatch b = new ProductionBatch();
        b.batchNumber = batchNumber;
        b.productType = productType;
        b.status = BatchStatus.PLANNED;
        b.apply(productionDate, inputWeightKg, notes, links);
        return b;
    }

    // FR-PROD-11: date, input, notes and links change only while PLANNED; links are replaced as a whole.
    public void edit(LocalDate productionDate, BigDecimal inputWeightKg, String notes, List<ProductionOrderItem> links) {
        requireStatus(BatchStatus.PLANNED, "EDITED");
        apply(productionDate, inputWeightKg, notes, links);
    }

    // PLANNED -> IN_PROGRESS. The service writes the CONSUMPTION movement in the same transaction.
    public void start(Instant at) {
        requireStatus(BatchStatus.PLANNED, BatchStatus.IN_PROGRESS);
        status = BatchStatus.IN_PROGRESS;
        startedAt = at;
    }

    // IN_PROGRESS -> COMPLETED, only when every kg is accounted for (FR-PROD-03/04): a difference must be stated as
    // a discrepancy, never absorbed silently. notes: the batch's notes afterwards.
    public void complete(BigDecimal output, BigDecimal wastage, BigDecimal discrepancy, String notes, Instant at) {
        requireStatus(BatchStatus.IN_PROGRESS, BatchStatus.COMPLETED);
        BigDecimal accounted = output.add(wastage).add(discrepancy);
        if (accounted.compareTo(inputWeightKg) != 0) {
            throw new ProductionNotReconciledException(batchNumber, inputWeightKg, accounted);
        }
        outputWeightKg = Weight.round(output);
        wastageWeightKg = Weight.round(wastage);
        discrepancyWeightKg = Weight.round(discrepancy);
        this.notes = notes;
        status = BatchStatus.COMPLETED;
        completedAt = at;
    }

    // PLANNED -> CANCELLED; a started batch is finished by completing it (FR-PROD-05).
    public void cancel() {
        requireStatus(BatchStatus.PLANNED, BatchStatus.CANCELLED);
        status = BatchStatus.CANCELLED;
    }

    // wastage / input x 100, 2 decimals; null until completed.
    public BigDecimal wastagePercent() {
        return wastageWeightKg == null ? null
                : wastageWeightKg.multiply(BigDecimal.valueOf(100)).divide(inputWeightKg, 2, RoundingMode.HALF_UP);
    }

    private void apply(LocalDate productionDate, BigDecimal inputWeightKg, String notes, List<ProductionOrderItem> links) {
        this.productionDate = productionDate;
        this.inputWeightKg = Weight.round(inputWeightKg);
        this.notes = notes;
        this.orderItems.clear();
        this.orderItems.addAll(links);
    }

    private void requireStatus(BatchStatus required, Object to) {
        if (status != required) {
            throw new InvalidStateTransitionException("Batch " + batchNumber, status, to);
        }
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public LocalDate getProductionDate() {
        return productionDate;
    }

    public ProductType getProductType() {
        return productType;
    }

    public BigDecimal getInputWeightKg() {
        return inputWeightKg;
    }

    public BigDecimal getOutputWeightKg() {
        return outputWeightKg;
    }

    public BigDecimal getWastageWeightKg() {
        return wastageWeightKg;
    }

    public BigDecimal getDiscrepancyWeightKg() {
        return discrepancyWeightKg;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getNotes() {
        return notes;
    }

    public List<ProductionOrderItem> getOrderItems() {
        return List.copyOf(orderItems);
    }
}

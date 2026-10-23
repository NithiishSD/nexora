package com.nexora.inventory.application;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.api.dto.MovementResponse;
import com.nexora.inventory.api.dto.OpeningStockRequest;
import com.nexora.inventory.api.dto.RecordWastageRequest;
import com.nexora.inventory.api.dto.StockAdjustmentRequest;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.error.FieldValidationException;

// The three stock changes the owner makes by hand (E-INV-05..07). Each is one use case = one transaction and,
// like every other stock change, goes through InventoryService (locks, checks, ledger row). Audit entries
// (INVENTORY_ADJUSTED) are added when audit_log arrives (T3.06); meanwhile each writes an INFO event line.
@Service
public class InventoryAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(InventoryAdjustmentService.class);

    private final InventoryService inventory;
    private final InventoryQueryService queries;
    private final AuditService audit;

    public InventoryAdjustmentService(InventoryService inventory, InventoryQueryService queries, AuditService audit) {
        this.inventory = inventory;
        this.queries = queries;
        this.audit = audit;
    }

    // Wastage found outside production (moisture, spillage). FR-INV-11.
    @Transactional
    public MovementResponse recordWastage(RecordWastageRequest r) {
        MovementResult result = inventory.recordMovement(new MovementCommand(r.productType(), null,
                MovementType.WASTAGE, r.fromLocation(), StockLocation.WASTAGE, r.quantityKg(), r.movementDate(),
                null, null, null, r.reason(), null));
        audit.record(AuditAction.INVENTORY_ADJUSTED, "MATERIAL_MOVEMENT", result.id(), r.reason(), Map.of(
                "movementType", "WASTAGE", "productType", r.productType().name(),
                "fromLocation", r.fromLocation().name(), "quantityKg", r.quantityKg().toPlainString()));
        log.info("event=inventory_wastage_recorded number={} kg={}", result.movementNumber(), r.quantityKg());
        return queries.movement(result.id());
    }

    // Correction after a physical count; never below zero (ADR-026). FR-INV-12.
    @Transactional
    public MovementResponse adjust(StockAdjustmentRequest r) {
        MovementResult result = inventory.recordMovement(new MovementCommand(r.productType(), null,
                MovementType.ADJUSTMENT, r.fromLocation(), r.toLocation(), r.quantityKg(), r.movementDate(),
                null, null, null, r.reason(), r.notes()));
        audit.record(AuditAction.INVENTORY_ADJUSTED, "MATERIAL_MOVEMENT", result.id(), r.reason(), Map.of(
                "movementType", "ADJUSTMENT", "productType", r.productType().name(),
                "fromLocation", String.valueOf(r.fromLocation()), "toLocation", String.valueOf(r.toLocation()),
                "quantityKg", r.quantityKg().toPlainString()));
        log.info("event=inventory_adjusted number={} kg={}", result.movementNumber(), r.quantityKg());
        return queries.movement(result.id());
    }

    // Stock at go-live, once per product and internal location (FR-INV-13).
    @Transactional
    public MovementResponse recordOpeningStock(OpeningStockRequest r) {
        if (!r.location().isInternal()) {
            throw new FieldValidationException("location", "Opening stock can only be entered for "
                    + "RAW_STOCK, INTERNAL_WIP, EXTERNAL_WIP or FINISHED_STOCK.");
        }
        MovementResult result = inventory.recordMovement(new MovementCommand(r.productType(), null,
                MovementType.OPENING_BALANCE, null, r.location(), r.quantityKg(), r.movementDate(),
                null, null, null, null, r.notes()));
        log.info("event=inventory_opening_recorded number={} kg={}", result.movementNumber(), r.quantityKg());
        return queries.movement(result.id());
    }
}

package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.StockLocation;

// One internal location of GET /api/v1/reports/inventory-movements (E-RPT-08): closing = opening + in - out.
public record InventoryMovementReportLine(StockLocation location, BigDecimal openingKg,
                                          List<TypeLine> movementsByType, BigDecimal closingKg) {

    public record TypeLine(MovementType movementType, BigDecimal inKg, BigDecimal outKg) {
    }
}

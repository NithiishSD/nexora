package com.nexora.inventory.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

// What another use case asks the inventory to do: "move this much of this product from here to there,
// because of that record" (03-ARCHITECTURE 5.3). It carries intent only; the result (new balances) is
// computed by InventoryService.
public record MovementCommand(
        ProductType productType,                // what leaves fromLocation
        ProductType toProductType,              // what arrives at toLocation; null = same as productType
        MovementType movementType,
        StockLocation fromLocation,             // null only for OPENING_BALANCE
        StockLocation toLocation,
        BigDecimal quantityKg,                  // > 0, at most 3 decimals
        LocalDate movementDate,
        MovementReferenceType referenceType,    // the record that caused it (null for opening stock,
        UUID referenceId,                       //   adjustments and manual wastage)
        String referenceNumber,                 // its business number, e.g. RCT-2026-0001
        String reason,                          // required for ADJUSTMENT
        String notes) {

    // Compact constructor: runs before the fields are assigned. Only raw silk entering work in progress
    // changes product (raw silk -> the warp being made); everything else keeps its product.
    public MovementCommand {
        if (toProductType == null) {
            toProductType = productType;
        }
    }
}

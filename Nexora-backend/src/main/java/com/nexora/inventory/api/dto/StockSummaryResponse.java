package com.nexora.inventory.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

// GET /api/v1/inventory/summary (05-API-SPEC E-INV-01). Every number is computed by the backend from the
// balances and the ledger; the client only displays it. Weights in kg with 3 decimals.
public record StockSummaryResponse(
        List<Balance> balances,               // every legal (product, internal location) pair, zero rows included
        BigDecimal rawSilkAvailableKg,        // RAW_SILK in RAW_STOCK
        BigDecimal internalWipKg,             // everything in INTERNAL_WIP
        BigDecimal externalWipKg,             // everything with manufacturers (EXTERNAL_WIP)
        BigDecimal finishedSilkWarpKg,
        BigDecimal finishedKoraWarpKg,
        BigDecimal vudaWarpKg,                // VUDA_WARP in RAW_STOCK (trading stock)
        List<Wastage> wastageToDateKg,        // cumulative movements into WASTAGE per product
        String unit,                          // always "kg"
        Instant asOf) {

    public record Balance(
            ProductType productType,
            StockLocation location,
            BigDecimal quantityKg,
            BigDecimal minThresholdKg,
            boolean isLowStock) {

        public Balance(ProductType productType, StockLocation location, BigDecimal quantityKg) {
            this(productType, location, quantityKg, null, false);
        }
    }

    public record Wastage(ProductType productType, BigDecimal quantityKg) {
    }
}

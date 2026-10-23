package com.nexora.inventory.domain;

// Where material is. Only the four internal locations have a balance; the outside ones are the
// sources and sinks of the ledger (a delivery goes to CUSTOMER and is simply gone from our stock).
public enum StockLocation {
    SUPPLIER(false),
    RAW_STOCK(true),
    INTERNAL_WIP(true),
    EXTERNAL_WIP(true),
    FINISHED_STOCK(true),
    CUSTOMER(false),
    WASTAGE(false),
    RETURNED(false);

    private final boolean internal;

    StockLocation(boolean internal) {
        this.internal = internal;
    }

    // True for RAW_STOCK, INTERNAL_WIP, EXTERNAL_WIP and FINISHED_STOCK.
    public boolean isInternal() {
        return internal;
    }

    // Which products this location may hold (plan decision R2, same table as the database CHECK):
    // raw stock = raw silk and vuda warp; work in progress and finished stock = silk and kora warp;
    // a supplier only supplies raw silk or vuda warp; the other outside locations take anything.
    public boolean holds(ProductType product) {
        return switch (this) {
            case RAW_STOCK, SUPPLIER -> product.isTradingStock();
            case INTERNAL_WIP, EXTERNAL_WIP, FINISHED_STOCK -> product.isWarpMadeHere();
            case CUSTOMER, WASTAGE, RETURNED -> true;
        };
    }
}

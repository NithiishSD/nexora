package com.nexora.inventory.domain;

// What the business holds, always measured in kg (DOMAIN_RULES section 3).
public enum ProductType {
    RAW_SILK,     // yarn bought from suppliers
    SILK_WARP,    // made from raw silk (in house or by a manufacturer)
    KORA_WARP,    // made from raw silk
    VUDA_WARP;    // bought and resold as it is: trading stock, never produced (FR-INV-10)

    // The two warp types that work in progress and finished stock hold.
    public boolean isWarpMadeHere() {
        return this == SILK_WARP || this == KORA_WARP;
    }

    // Raw silk and vuda warp: what a supplier delivers and what raw stock holds.
    public boolean isTradingStock() {
        return this == RAW_SILK || this == VUDA_WARP;
    }
}

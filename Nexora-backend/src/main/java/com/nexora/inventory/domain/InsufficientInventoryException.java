package com.nexora.inventory.domain;

import java.math.BigDecimal;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// Stock would go negative. 409 INSUFFICIENT_INVENTORY; the message names what is available and where.
public class InsufficientInventoryException extends BusinessException {

    public InsufficientInventoryException(ProductType product, StockLocation location, BigDecimal availableKg) {
        super(ErrorCode.INSUFFICIENT_INVENTORY,
                "Only " + availableKg.toPlainString() + " kg of " + product + " available in " + location + ".");
    }
}

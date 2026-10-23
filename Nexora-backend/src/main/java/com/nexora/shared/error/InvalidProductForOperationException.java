package com.nexora.shared.error;

// The product is not allowed for this operation: vuda warp into production, a silk warp purchase,
// a product stored at a location that never holds it. 422 INVALID_PRODUCT_FOR_OPERATION.
// Shared because inventory, procurement, orders, production and outsourcing all raise it.
public class InvalidProductForOperationException extends BusinessException {

    public InvalidProductForOperationException(String message) {
        super(ErrorCode.INVALID_PRODUCT_FOR_OPERATION, message);
    }
}

package com.nexora.orders.domain;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// Edit or cancel after work started, or after payments were allocated to the order (FR-ORD-06/08). 409 ORDER_LOCKED.
public class OrderLockedException extends BusinessException {

    public OrderLockedException(String orderNumber, String why) {
        super(ErrorCode.ORDER_LOCKED, "Order " + orderNumber + " can no longer be changed: " + why + ".");
    }
}

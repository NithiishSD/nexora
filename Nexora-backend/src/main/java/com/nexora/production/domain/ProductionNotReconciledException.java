package com.nexora.production.domain;

import java.math.BigDecimal;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// 422: what went in does not equal what came out plus wastage plus the stated discrepancy (FR-PROD-03/04).
public class ProductionNotReconciledException extends BusinessException {

    public ProductionNotReconciledException(String batchNumber, BigDecimal input, BigDecimal accounted) {
        super(ErrorCode.PRODUCTION_NOT_RECONCILED, "Batch " + batchNumber + " used " + input.toPlainString()
                + " kg but output + wastage + discrepancy is " + accounted.toPlainString()
                + " kg; enter the difference as a discrepancy.");
    }
}

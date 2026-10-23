package com.nexora.procurement.domain;

import java.math.BigDecimal;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// More would be received than was ordered (FR-PROC-08). 409 PURCHASE_RECEIPT_EXCEEDED.
public class PurchaseReceiptExceededException extends BusinessException {

    public PurchaseReceiptExceededException(String purchaseNumber, BigDecimal remainingKg) {
        super(ErrorCode.PURCHASE_RECEIPT_EXCEEDED, "Only " + remainingKg.toPlainString()
                + " kg of purchase " + purchaseNumber + " remain to be received.");
    }
}

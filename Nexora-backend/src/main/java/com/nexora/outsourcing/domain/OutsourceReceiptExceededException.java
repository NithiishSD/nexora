package com.nexora.outsourcing.domain;

import java.math.BigDecimal;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// 409: a receipt (received + wastage) would account for more than is still outside with the manufacturer (FR-OUT-03).
public class OutsourceReceiptExceededException extends BusinessException {

    public OutsourceReceiptExceededException(String jobNumber, BigDecimal outside, BigDecimal requested) {
        super(ErrorCode.OUTSOURCE_RECEIPT_EXCEEDED, "Job " + jobNumber + " has " + outside.toPlainString()
                + " kg left to account for; this receipt accounts for " + requested.toPlainString() + " kg.");
    }
}

package com.nexora.finance.domain;

import java.math.BigDecimal;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// 409: an OUT transaction would make a cash or bank account negative (FR-FIN-06; no overdraft, ADR-031).
public class InsufficientFundsException extends BusinessException {

    public InsufficientFundsException(String accountName, BigDecimal balance, BigDecimal amount) {
        super(ErrorCode.INSUFFICIENT_FUNDS, "Only " + balance.toPlainString() + " available in " + accountName
                + "; cannot pay out " + amount.toPlainString() + ".");
    }
}

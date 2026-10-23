package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.nexora.finance.api.dto.AccountRef;
import com.nexora.finance.domain.TransactionType;

// One account of GET /api/v1/reports/cash-bank (E-RPT-12): closing = opening + totalIn - totalOut.
public record CashBankReportLine(AccountRef account, BigDecimal openingBalance, List<TypeTotal> inByType,
                                 List<TypeTotal> outByType, BigDecimal totalIn, BigDecimal totalOut,
                                 BigDecimal closingBalance) {

    public record TypeTotal(TransactionType transactionType, BigDecimal amount) {
    }
}

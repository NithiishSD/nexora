package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// GET /api/v1/finance/cash-position (E-FIN-08): physical cash, each bank and the money available in total.
public record CashPositionResponse(
        List<AccountBalance> cash,
        List<AccountBalance> banks,
        BigDecimal totalCash,
        BigDecimal totalBank,
        BigDecimal totalAvailable,
        Instant asOf) {

    public record AccountBalance(AccountRef account, BigDecimal balance) {
    }
}

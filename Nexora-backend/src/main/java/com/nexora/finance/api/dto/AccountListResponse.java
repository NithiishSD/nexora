package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.util.List;

// GET /api/v1/finance/accounts (E-FIN-02): the accounts and the money available in all of them.
public record AccountListResponse(List<AccountResponse> accounts, BigDecimal totalAvailable) {
}

package com.nexora.finance.api;

import java.time.Clock;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.finance.api.dto.CashPositionResponse;
import com.nexora.finance.application.FinancialAccountService;

// Money in hand right now: physical cash, each bank and the total (FR-FIN-08).
@RestController
class CashPositionController {

    private final FinancialAccountService accounts;
    private final Clock clock;

    CashPositionController(FinancialAccountService accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @GetMapping("/api/v1/finance/cash-position")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    CashPositionResponse cashPosition() {
        return accounts.cashPosition(clock.instant());
    }
}

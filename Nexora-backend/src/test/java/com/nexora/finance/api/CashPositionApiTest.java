package com.nexora.finance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class CashPositionApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-FIN-08 cash position cash banks and total")
    void cashPosition_cashBanksAndTotal() throws Exception {
        // FR-FIN-03 data: cash 80000, two banks 150000 and 100000; a closed account is left out
        String cash = createAccount("Cash", "CASH", "80000.00");
        createAccount("SBI Current", "BANK", "150000.00");
        createAccount("Canara Savings", "BANK", "100000.00");
        String old = createAccount("Old cash", "CASH", "0.00");
        asOwner(postJson("/api/v1/finance/accounts/" + old + "/close", "")).andExpect(status().isOk());

        // AC1: cash, both banks (by name) and the total available
        asOwner(get("/api/v1/finance/cash-position")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cash.length()").value(1))
                .andExpect(jsonPath("$.cash[0].account.id").value(cash))
                .andExpect(jsonPath("$.cash[0].balance").value(80000.00))
                .andExpect(jsonPath("$.banks.length()").value(2))
                .andExpect(jsonPath("$.banks[0].account.accountName").value("Canara Savings"))
                .andExpect(jsonPath("$.totalCash").value(80000.00))
                .andExpect(jsonPath("$.totalBank").value(250000.00))
                .andExpect(jsonPath("$.totalAvailable").value(330000.00))
                .andExpect(jsonPath("$.asOf").isNotEmpty());
        // AC2: no token -> 401; a token without FINANCE_VIEW -> 403
        mockMvc.perform(get("/api/v1/finance/cash-position")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/finance/cash-position").header(HttpHeaders.AUTHORIZATION,
                AuthTokens.bearer(tokens.mint(java.util.UUID.randomUUID(), java.util.List.of("ORDER_VIEW"),
                        clock.instant(), java.time.Duration.ofMinutes(5))))).andExpect(status().isForbidden());
    }
}

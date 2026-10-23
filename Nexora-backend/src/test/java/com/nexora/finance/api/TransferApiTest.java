package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class TransferApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-FIN-09 transfer creates out and in transactions not revenue")
    void transfer_createsOutAndInTransactionsNotRevenue() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "50000.00");
        String cash = createAccount("Cash", "CASH", "0.00");

        // AC1: bank 50000 -> 30000, cash 0 -> 20000, total unchanged; one TRANSFER_OUT and one TRANSFER_IN row
        String id = body(transfer(bank, cash, "20000.00").andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/finance/transfers/")))
                .andExpect(jsonPath("$.transferNumber").value("TRF-" + Year.now(clock) + "-0001"))
                .andExpect(jsonPath("$.sourceAccount.id").value(bank))
                .andExpect(jsonPath("$.destinationAccount.accountType").value("CASH"))
                .andExpect(jsonPath("$.amount").value(20000.00))
                .andExpect(jsonPath("$.outTransactionId").isNotEmpty())
                .andExpect(jsonPath("$.inTransactionId").isNotEmpty())).get("id").asString();
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(30000.00));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(20000.00));
        asOwner(get("/api/v1/finance/accounts")).andExpect(jsonPath("$.totalAvailable").value(50000.00));
        assertThat(jdbc.queryForList("select transaction_type from financial_transactions where account_transfer_id ="
                + " ?::uuid order by direction", String.class, id)).containsExactly("TRANSFER_IN", "TRANSFER_OUT");
        // not revenue and not expense: no other row type was written
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type not in"
                + " ('OPENING_BALANCE', 'TRANSFER_OUT', 'TRANSFER_IN')", Integer.class)).isZero();
        // view, list by either side, no edit or delete
        asOwner(get("/api/v1/finance/transfers/" + id)).andExpect(jsonPath("$.transferNumber").value(Matchers.startsWith("TRF-")));
        asOwner(get("/api/v1/finance/transfers?accountId=" + cash)).andExpect(jsonPath("$.totalElements").value(1));
        asOwner(get("/api/v1/finance/transfers?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest());
        asOwner(get("/api/v1/finance/transfers?sort=amount")).andExpect(status().isBadRequest());
        asOwner(put("/api/v1/finance/transfers/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(delete("/api/v1/finance/transfers/" + id)).andExpect(status().isMethodNotAllowed());
        asOwner(get("/api/v1/finance/transfers/" + UUID.randomUUID())).andExpect(status().isNotFound());
        // STAFF cannot move money
        mockMvc.perform(postWithKey("/api/v1/finance/transfers", body(bank, cash, "1.00"))
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-FIN-09 transfer same account returns400")
    void transfer_sameAccount_returns400() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "50000.00");

        // AC2
        transfer(bank, bank, "100.00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("destinationAccountId"));
        // amount 0 and an unknown account
        transfer(bank, createAccount("Cash", "CASH", "0.00"), "0.00").andExpect(status().isBadRequest());
        transfer(bank, UUID.randomUUID().toString(), "1.00").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from account_transfers", Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-FIN-09 FR-FIN-06 transfer above source balance returns409 insufficient funds")
    void transfer_aboveSourceBalance_returns409InsufficientFunds() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "10000.00");
        String cash = createAccount("Cash", "CASH", "0.00");

        // AC3: nothing is stored, neither row nor transfer, and the number is not used up
        transfer(bank, cash, "15000.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(jdbc.queryForObject("select count(*) from account_transfers", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where account_transfer_id is not"
                + " null", Integer.class)).isZero();
        // AC4: a closed account on the destination side is refused
        String closed = createAccount("Old cash", "CASH", "0.00");
        asOwner(postJson("/api/v1/finance/accounts/" + closed + "/close", "")).andExpect(status().isOk());
        transfer(bank, closed, "100.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        transfer(bank, cash, "10000.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferNumber").value(Matchers.endsWith("-0001")));
    }

    @Test
    @DisplayName("FR-FIN-18 transfer replay with same key creates one transfer")
    void transfer_replayWithSameKey_createsOneTransfer() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "50000.00");
        String cash = createAccount("Cash", "CASH", "0.00");

        replayTwice("/api/v1/finance/transfers", body(bank, cash, "5000.00"), 201);

        assertThat(jdbc.queryForObject("select count(*) from account_transfers", Integer.class)).isEqualTo(1);
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(5000.00));
        // a transfer needs a key
        asOwner(postJson("/api/v1/finance/transfers", body(bank, cash, "1.00"))).andExpect(status().isBadRequest());
    }

    private ResultActions transfer(String source, String destination, String amount) throws Exception {
        return asOwner(postWithKey("/api/v1/finance/transfers", body(source, destination, amount)));
    }

    private String body(String source, String destination, String amount) {
        return """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":%s,"transferDate":"%s"}"""
                .formatted(source, destination, amount, today());
    }
}

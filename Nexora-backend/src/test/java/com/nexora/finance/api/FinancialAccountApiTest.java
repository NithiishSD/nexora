package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class FinancialAccountApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-FIN-01 NFR-SEC-06 create bank account stores last four digits only")
    void create_bankAccount_storesLastFourDigitsOnly() throws Exception {
        // AC1: 201 + Location, ACTIVE, only XXXXXX4821 is stored and returned
        asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"SBI Current","accountType":"BANK","bankName":"State Bank of India",
                 "accountNumberLast4":"4821"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/finance/accounts/")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.maskedAccountNumber").value("XXXXXX4821"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.version").doesNotExist());
        assertThat(jdbc.queryForObject("select masked_account_number from financial_accounts", String.class))
                .isEqualTo("XXXXXX4821");

        // AC2: same name -> 409 DUPLICATE_RESOURCE, nothing stored
        asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"SBI Current","accountType":"BANK","bankName":"SBI"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
        // AC3: a full account number is refused before anything is stored
        asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"HDFC","accountType":"BANK","bankName":"HDFC","accountNumberLast4":"123456784821"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("accountNumberLast4"));
        // AC4: bank fields on a cash account; a bank account without its bank name
        asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"Cash","accountType":"CASH","bankName":"SBI"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bankName"));
        asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"Indian Bank","accountType":"BANK"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bankName"));
        assertThat(jdbc.queryForObject("select count(*) from financial_accounts", Integer.class)).isEqualTo(1);

        // STAFF cannot create accounts
        mockMvc.perform(postJson("/api/v1/finance/accounts", "{\"accountName\":\"X\",\"accountType\":\"CASH\"}")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-FIN-02 opening balance is transaction not column")
    void openingBalance_isTransactionNotColumn() throws Exception {
        String id = createAccount("Cash", "CASH", "80000.00");

        // AC1: one OPENING_BALANCE IN transaction of 80000.00 and the balance computed from it
        asOwner(get("/api/v1/finance/transactions?accountId=" + id))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionType").value("OPENING_BALANCE"))
                .andExpect(jsonPath("$.content[0].direction").value("IN"))
                .andExpect(jsonPath("$.content[0].amount").value(80000.00))
                .andExpect(jsonPath("$.content[0].referenceType").value("NONE"));
        asOwner(get("/api/v1/finance/accounts/" + id)).andExpect(jsonPath("$.balance").value(80000.00));
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name ="
                + " 'financial_accounts' and column_name like '%balance%'", Integer.class)).isZero();

        // AC2: negative -> 400; a positive opening needs its date; zero writes no transaction
        asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"Petty","accountType":"CASH","openingBalance":-1.00,"openingDate":"%s"}"""
                .formatted(today()))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("openingBalance"));
        asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"Petty","accountType":"CASH","openingBalance":10.00}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("openingDate"));
        createAccount("Petty", "CASH", "0.00");
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-FIN-03 list balances derived from transactions")
    void list_balancesDerivedFromTransactions() throws Exception {
        createAccount("Cash", "CASH", "80000.00");
        createAccount("SBI Current", "BANK", "200000.00");
        createAccount("Indian Bank", "BANK", "50000.00");
        // AC2: receivables are not money in hand
        String order = createOrder(data.customer().id(), "RAW_SILK", "100.000", "5000.00");
        asOwner(postJson("/api/v1/orders/" + order + "/confirm", "")).andExpect(status().isOk());

        // AC1: 80000 + 200000 + 50000
        asOwner(get("/api/v1/finance/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(3))
                .andExpect(jsonPath("$.accounts[0].accountName").value("Indian Bank"))
                .andExpect(jsonPath("$.totalAvailable").value(330000.00));
        // a filter narrows the list, never the total of money in hand
        asOwner(get("/api/v1/finance/accounts?accountType=CASH"))
                .andExpect(jsonPath("$.accounts.length()").value(1))
                .andExpect(jsonPath("$.accounts[0].balance").value(80000.00))
                .andExpect(jsonPath("$.totalAvailable").value(330000.00));
        // AC3: STAFF has no FINANCE_VIEW
        mockMvc.perform(get("/api/v1/finance/accounts").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        asOwner(get("/api/v1/finance/accounts/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-FIN-04 post transaction route does not exist")
    void postTransaction_routeDoesNotExist() throws Exception {
        String account = createAccount("Cash", "CASH", "100.00");
        String tx = body(asOwner(get("/api/v1/finance/transactions?accountId=" + account))).get("content").get(0)
                .get("id").asString();
        asOwner(get("/api/v1/finance/transactions/" + tx)).andExpect(status().isOk())
                .andExpect(jsonPath("$.account.accountName").value("Cash"));

        // AC1: nobody can post a transaction; AC2: nor change or delete one
        asOwner(postJson("/api/v1/finance/transactions", """
                {"accountId":"%s","direction":"IN","amount":1000000.00}""".formatted(account)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        asOwner(put("/api/v1/finance/transactions/" + tx).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(patch("/api/v1/finance/transactions/" + tx).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(delete("/api/v1/finance/transactions/" + tx)).andExpect(status().isMethodNotAllowed());
        asOwner(delete("/api/v1/finance/accounts/" + account)).andExpect(status().isMethodNotAllowed());
        asOwner(get("/api/v1/finance/accounts/" + account)).andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    @DisplayName("FR-FIN-15 adjustment requires reason and permission")
    void adjustment_requiresReasonAndPermission() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "49000.00");

        // AC2: no reason (or blank) -> 400; no key -> 400
        asOwner(postWithKey(adjustments(bank), """
                {"direction":"IN","amount":1000.00,"transactionDate":"%s"}""".formatted(today())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        asOwner(postWithKey(adjustments(bank), adjustment("IN", "1000.00", " "))).andExpect(status().isBadRequest());
        asOwner(postJson(adjustments(bank), adjustment("IN", "1000.00", "statement"))).andExpect(status().isBadRequest());
        // FINANCE_MANAGE only: Staff 403; unknown account 404
        mockMvc.perform(postWithKey(adjustments(bank), adjustment("IN", "1000.00", "statement"))
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
        asOwner(postWithKey(adjustments(java.util.UUID.randomUUID().toString()), adjustment("IN", "1.00", "x")))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type ="
                + " 'ADJUSTMENT'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-FIN-15 NFR-OBS-06 adjustment writes adjustment transaction and account adjusted audit")
    void adjustment_writesAdjustmentTransactionAndAccountAdjustedAudit() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "49000.00");

        // AC1: 49000 vs a statement of 50000 -> IN 1000 -> 50000; the row has no business reference
        String id = body(asOwner(postWithKey(adjustments(bank), adjustment("IN", "1000.00", "statement 2026-09-30")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/finance/transactions/")))
                .andExpect(jsonPath("$.transactionType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.direction").value("IN"))
                .andExpect(jsonPath("$.referenceType").value("NONE"))
                .andExpect(jsonPath("$.description").value("statement 2026-09-30"))).get("id").asString();
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(50000.00));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ACCOUNT_ADJUSTED'"
                + " and entity_id = ?::uuid and reason = 'statement 2026-09-30' and details ->> 'transactionId' = ?",
                Integer.class, bank, id)).isEqualTo(1);
        // the history shows it with the running balance
        asOwner(get("/api/v1/finance/accounts/" + bank + "/transactions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].transactionType").value("OPENING_BALANCE"))
                .andExpect(jsonPath("$.content[0].runningBalance").value(49000.00))
                .andExpect(jsonPath("$.content[1].runningBalance").value(50000.00));
        asOwner(postWithKey(adjustments(bank), adjustment("OUT", "500.00", "count"))).andExpect(status().isCreated());
        asOwner(get("/api/v1/finance/accounts/" + bank + "/transactions?type=ADJUSTMENT"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].runningBalance").value(49500.00));
    }

    @Test
    @DisplayName("FR-FIN-15 FR-FIN-06 adjustment decrease below zero returns409 insufficient funds")
    void adjustmentDecreaseBelowZero_returns409InsufficientFunds() throws Exception {
        String cash = createAccount("Cash", "CASH", "1000.00");

        // AC3: OUT 1500 on 1000 -> 409, nothing stored; a closed account -> 409
        asOwner(postWithKey(adjustments(cash), adjustment("OUT", "1500.00", "count"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(1000.00));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ACCOUNT_ADJUSTED'",
                Integer.class)).isZero();
        asOwner(postWithKey(adjustments(cash), adjustment("OUT", "1000.00", "to zero"))).andExpect(status().isCreated());
        asOwner(postJson("/api/v1/finance/accounts/" + cash + "/close", "")).andExpect(status().isOk());
        asOwner(postWithKey(adjustments(cash), adjustment("IN", "1.00", "late"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("FR-FIN-18 adjustment replay with same key returns stored response")
    void adjustment_replayWithSameKey_returnsStoredResponse() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "1000.00");

        replayTwice(adjustments(bank), adjustment("IN", "250.00", "statement"), 201);

        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(1250.00));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ACCOUNT_ADJUSTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-FIN-16 close with non zero balance is rejected")
    void close_withNonZeroBalance_isRejected() throws Exception {
        String bank = createAccount("Old bank", "BANK", "100.00");

        // AC2: 100.00 left -> 409, still ACTIVE
        asOwner(postJson("/api/v1/finance/accounts/" + bank + "/close", "")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.status").value("ACTIVE"));
        // AC1: at 0.00 -> CLOSED, history still listed; closing twice -> 409; Staff 403
        asOwner(postWithKey(adjustments(bank), adjustment("OUT", "100.00", "moved to cash"))).andExpect(status().isCreated());
        mockMvc.perform(postJson("/api/v1/finance/accounts/" + bank + "/close", "")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
        asOwner(postJson("/api/v1/finance/accounts/" + bank + "/close", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.balance").value(0));
        asOwner(get("/api/v1/finance/accounts/" + bank + "/transactions"))
                .andExpect(jsonPath("$.content.length()").value(2));
        asOwner(postJson("/api/v1/finance/accounts/" + bank + "/close", "")).andExpect(status().isConflict());
        asOwner(postJson("/api/v1/finance/accounts/" + java.util.UUID.randomUUID() + "/close", ""))
                .andExpect(status().isNotFound());
    }

    private static String adjustments(String account) {
        return "/api/v1/finance/accounts/" + account + "/adjustments";
    }

    private String adjustment(String direction, String amount, String reason) {
        return """
                {"direction":"%s","amount":%s,"transactionDate":"%s","reason":"%s"}""".formatted(direction, amount,
                today(), reason);
    }
}

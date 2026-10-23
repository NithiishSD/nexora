package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class PaymentReversalApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-07 reverse replay with same key returns stored response")
    void reverse_replayWithSameKey_returnsStoredResponse() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "1000.00");
        String id = customerPayment(customer, "300.00", cash);

        replayTwice("/api/v1/payments/" + id + "/reverse", "{\"reason\":\"entered twice\"}", 200);

        // one REVERSAL row and one audit entry; cash is back at 1000
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type = 'REVERSAL'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'PAYMENT_REVERSED'",
                Integer.class)).isEqualTo(1);
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(1000.00));
    }

    @Test
    @DisplayName("FR-PAY-15 FR-FIN-17 reverse marks reversed and adds compensating transaction")
    void reverse_marksReversedAndAddsCompensatingTransaction() throws Exception {
        UUID customer = data.customer().id();
        String bank = createAccount("SBI Current", "BANK", "0.00");
        String a = confirmedOrder(customer, "KORA_WARP", "20.000", "1000.00");
        String b = confirmedOrder(customer, "KORA_WARP", "30.000", "1000.00");
        String id = customerPayment(customer, "45000.00", bank);

        // AC1: REVERSED with time and reason; A and B owed again; the account falls by 45000
        asOwner(reverse(id, "Entered against the wrong customer")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.reversalReason").value("Entered against the wrong customer"))
                .andExpect(jsonPath("$.reversedAt").isString())
                .andExpect(jsonPath("$.reversalTransactionId").isString())
                .andExpect(jsonPath("$.amount").value(45000.00))
                .andExpect(jsonPath("$.allocations[0].released").value(true));
        asOwner(get("/api/v1/orders/" + a)).andExpect(jsonPath("$.outstandingAmount").value(20000.00));
        asOwner(get("/api/v1/orders/" + b)).andExpect(jsonPath("$.outstandingAmount").value(30000.00));
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(0.0));
        // FR-FIN-17 AC1: the original row is untouched and the REVERSAL mirrors it; together they net to zero
        asOwner(get("/api/v1/finance/transactions?referenceType=PAYMENT&referenceId=" + id + "&sort=createdAt,asc"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].transactionType").value("CUSTOMER_PAYMENT"))
                .andExpect(jsonPath("$.content[0].direction").value("IN"))
                .andExpect(jsonPath("$.content[0].reversedByTransactionId").isString())
                .andExpect(jsonPath("$.content[1].transactionType").value("REVERSAL"))
                .andExpect(jsonPath("$.content[1].direction").value("OUT"))
                .andExpect(jsonPath("$.content[1].amount").value(45000.00))
                .andExpect(jsonPath("$.content[1].reversesTransactionId").isString());
        // the customer's outstanding counts the orders again and shows no advance
        asOwner(get("/api/v1/customers/" + customer + "/outstanding")).andExpect(jsonPath("$.totalOutstanding")
                .value(50000.00)).andExpect(jsonPath("$.advance").value(0.0));

        // AC2: reversing again -> 409; AC4: a blank reason -> 400; unknown -> 404
        asOwner(reverse(id, "again")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        asOwner(reverse(id, " ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        asOwner(reverse(UUID.randomUUID().toString(), "x")).andExpect(status().isNotFound());

        // AC3: the money of a customer payment was spent -> reversing it would make the account negative -> 409
        String cash = createAccount("Cash", "CASH", "0.00");
        String spent = customerPayment(customer, "1000.00", cash);
        // the cash was counted short and corrected by an adjustment: only 400 is left of the 1000
        jdbc.update("insert into financial_transactions (financial_account_id, transaction_date, transaction_type,"
                + " direction, amount, description) values (?::uuid, ?, 'ADJUSTMENT', 'OUT', 600.00, 'counted short')",
                cash, today());
        asOwner(reverse(spent, "wrong")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        asOwner(get("/api/v1/payments/" + spent)).andExpect(jsonPath("$.status").value("RECORDED"));

        // a supplier payment reversed brings the money back IN and reopens the purchase
        String supplierCash = createAccount("Supplier cash", "CASH", "50000.00");
        UUID supplier = data.supplier().id();
        String purchase = createPurchase(supplier, "RAW_SILK", "10.000", "1000.00");
        receive(purchase, "10.000", "10.000", "0.000").andExpect(status().isCreated());
        String paid = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"SUPPLIER","partyId":"%s","amount":10000.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(supplier, supplierCash))).andExpect(status().isCreated())).get("id").asString();
        asOwner(reverse(paid, "paid the wrong bill")).andExpect(status().isOk());
        asOwner(get("/api/v1/finance/accounts/" + supplierCash)).andExpect(jsonPath("$.balance").value(50000.00));
        asOwner(get("/api/v1/purchases/" + purchase)).andExpect(jsonPath("$.outstandingAmount").value(10000.00));
    }

    @Test
    @DisplayName("FR-PAY-15 reverse without permission returns403")
    void reverse_withoutPermission_returns403() throws Exception {
        UUID customer = data.customer().id();
        String id = customerPayment(customer, "100.00", createAccount("Cash", "CASH", "0.00"));
        // STAFF has no PAYMENT_REVERSE; a token with PAYMENT_CREATE only neither
        mockMvc.perform(reverse(id, "x").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        String createOnly = tokens.mint(UUID.fromString(jdbc.queryForObject("select id::text from users where"
                + " username = 'owner'", String.class)), java.util.List.of("PAYMENT_CREATE", "PAYMENT_VIEW"),
                clock.instant(), java.time.Duration.ofMinutes(5));
        mockMvc.perform(reverse(id, "x").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(createOnly)))
                .andExpect(status().isForbidden());
        asOwner(get("/api/v1/payments/" + id)).andExpect(jsonPath("$.status").value("RECORDED"));
    }

    @Test
    @DisplayName("NFR-OBS-06 reverse writes audit entry with reason")
    void reverse_writesAuditEntryWithReason() throws Exception {
        UUID customer = data.customer().id();
        String id = customerPayment(customer, "250.00", createAccount("Cash", "CASH", "0.00"));

        asOwner(reverse(id, "duplicate entry")).andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("select action, entity_type, reason, actor_user_id::text as actor,"
                + " details->>'amount' as amount from audit_log where entity_id = ?::uuid", id);
        assertThat(row).containsEntry("action", "PAYMENT_REVERSED").containsEntry("entity_type", "PAYMENT")
                .containsEntry("reason", "duplicate entry").containsEntry("amount", "250.00");
        assertThat(row.get("actor")).isEqualTo(jdbc.queryForObject("select id::text from users where username ="
                + " 'owner'", String.class));
        // a refused reversal leaves no audit row
        asOwner(reverse(id, "again")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'PAYMENT_REVERSED'",
                Integer.class)).isEqualTo(1);
        asOwner(get("/api/v1/payments/" + id)).andExpect(jsonPath("$.reversalReason").value(Matchers.is("duplicate entry")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reverse(String id, String reason) {
        return postWithKey("/api/v1/payments/" + id + "/reverse", "{\"reason\":\"" + reason + "\"}");
    }

    private ResultActions asOwnerPost(String path, String body) throws Exception {
        return asOwner(postWithKey(path, body));
    }

    private String customerPayment(UUID customer, String amount, String account) throws Exception {
        String method = jdbc.queryForObject("select account_type from financial_accounts where id = ?::uuid",
                String.class, account).equals("CASH") ? "CASH" : "UPI";
        return body(asOwnerPost("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":%s,"paymentMethod":"%s","financialAccountId":"%s"}"""
                .formatted(customer, amount, method, account)).andExpect(status().isCreated())).get("id").asString();
    }
}

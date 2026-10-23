package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class CustomerStatementApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-04 statement opening debits credits closing")
    void statement_openingDebitsCreditsClosing() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "0.00");
        String customer = data.customer().id().toString();
        // before the period: order 10000 (20 days ago), payment 4000 (15 days ago) -> opening 6000
        owedOrder(customer, today().minusDays(20), "10000.00");
        pay(customer, "4000.00", today().minusDays(15), bank);
        // in the period: order 20000, payment 30000 (4000 of it an advance), a 1000 payment entered by mistake and
        // reversed; a PLACED order is not owed and not listed
        owedOrder(customer, today().minusDays(5), "20000.00");
        asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s",
                 "items":[{"productType":"RAW_SILK","requiredWeightKg":1.000,"ratePerKg":999.00}]}"""
                .formatted(customer, today()))).andExpect(status().isCreated());
        pay(customer, "30000.00", today(), bank);
        String mistake = pay(customer, "1000.00", today(), bank);
        asOwner(postWithKey("/api/v1/payments/" + mistake + "/reverse", "{\"reason\":\"entered twice\"}"))
                .andExpect(status().isOk());

        String path = "/api/v1/customers/" + customer + "/statement?from=" + today().minusDays(10) + "&to=" + today();
        asOwner(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.id").value(customer))
                .andExpect(jsonPath("$.openingBalance").value(6000.00))
                .andExpect(jsonPath("$.lines.length()").value(4))
                .andExpect(jsonPath("$.lines[0].kind").value("ORDER"))
                .andExpect(jsonPath("$.lines[0].debit").value(20000.00))
                .andExpect(jsonPath("$.lines[0].runningBalance").value(26000.00))
                .andExpect(jsonPath("$.lines[1].kind").value("PAYMENT"))
                .andExpect(jsonPath("$.lines[1].credit").value(30000.00))
                .andExpect(jsonPath("$.lines[1].runningBalance").value(-4000.00))
                .andExpect(jsonPath("$.lines[2].runningBalance").value(-5000.00))
                .andExpect(jsonPath("$.lines[3].kind").value("REVERSAL"))
                .andExpect(jsonPath("$.lines[3].debit").value(1000.00))
                .andExpect(jsonPath("$.closingBalance").value(-4000.00));
        // AC1: closing = outstanding - advance (0 - 4000)
        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(jsonPath("$.totalOutstanding").value(0.0)).andExpect(jsonPath("$.advance").value(4000.00));

        // AC2: not a customer -> 422; STAFF -> 403; from after to -> 400; unknown -> 404
        asOwner(get("/api/v1/customers/" + data.supplier().id() + "/statement?from=" + today() + "&to=" + today()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        asOwner(get("/api/v1/customers/" + customer + "/statement?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        asOwner(get("/api/v1/customers/" + java.util.UUID.randomUUID() + "/statement?from=" + today() + "&to="
                + today())).andExpect(status().isNotFound());
    }

    private void owedOrder(String customer, LocalDate date, String amount) throws Exception {
        String id = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s",
                 "items":[{"productType":"RAW_SILK","requiredWeightKg":1.000,"ratePerKg":%s}]}"""
                .formatted(customer, date, amount))).andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());
    }

    private String pay(String customer, String amount, LocalDate date, String bank) throws Exception {
        return body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":%s,"paymentMethod":"UPI","financialAccountId":"%s",
                 "paymentDate":"%s"}""".formatted(customer, amount, bank, date))).andExpect(status().isCreated()))
                .get("id").asString();
    }
}

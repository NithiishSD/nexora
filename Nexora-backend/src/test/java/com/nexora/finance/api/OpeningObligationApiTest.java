package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class OpeningObligationApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-22 opening receivable is allocatable oldest first")
    void openingReceivable_isAllocatableOldestFirst() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "50000.00");
        String order = confirmedOrder(customer, "RAW_SILK", "10.000", "1000.00");          // 10000, dated today
        String opening = body(open("CUSTOMER", customer, "2026-03-31", "25000.00", "Ledger book 3 page 41")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/opening-obligations/")))
                .andExpect(jsonPath("$.outstanding").value(25000.00))).get("id").asString();

        // AC1: the opening receivable is the oldest obligation, so 10000 goes to it first; the order stays unpaid
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":10000.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(customer, cash))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocations.length()").value(1))
                .andExpect(jsonPath("$.allocations[0].targetType").value("OPENING_OBLIGATION"))
                .andExpect(jsonPath("$.allocations[0].targetId").value(opening))
                .andExpect(jsonPath("$.allocations[0].targetLabel").value("Opening CUS 2026-03-31"));
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        asOwner(get("/api/v1/opening-obligations?partyId=" + customer))
                .andExpect(jsonPath("$.content[0].allocated").value(10000.00))
                .andExpect(jsonPath("$.content[0].outstanding").value(15000.00));
        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(jsonPath("$.totalOutstanding").value(25000.00))
                .andExpect(jsonPath("$.openingObligations[0].outstanding").value(15000.00))
                .andExpect(jsonPath("$.ageing.over90").value(15000.00));
        // not revenue, not stock: no ledger row and no movement came from entering it
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from material_movements", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select reason from audit_log where action = 'OPENING_BALANCE_IMPORTED'",
                String.class)).isEqualTo("Ledger book 3 page 41");

        // a supplier's opening payable is paid before its purchases, and shows on the supplier's payables
        UUID supplier = data.supplier().id();
        open("SUPPLIER", supplier, "2026-02-15", "8000.00", "Old bill 77").andExpect(status().isCreated());
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"SUPPLIER","partyId":"%s","amount":3000.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(supplier, cash))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocations[0].targetLabel").value("Opening SUP 2026-02-15"));
        asOwner(get("/api/v1/suppliers/" + supplier + "/payables"))
                .andExpect(jsonPath("$.totalPayable").value(5000.00))
                .andExpect(jsonPath("$.openingObligations[0].outstanding").value(5000.00));
        asOwner(get("/api/v1/opening-obligations?outstandingOnly=true&sort=originalAmount,desc"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].originalAmount").value(25000.00));

        // AC2: a future date, or a due date before it -> 400; AC3: a party without the role -> 422; STAFF -> 403
        open("CUSTOMER", customer, today().plusDays(1).toString(), "1.00", "x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("obligationDate"));
        asOwner(postJson("/api/v1/opening-obligations", """
                {"purpose":"CUSTOMER","partyId":"%s","obligationDate":"2026-03-31","dueDate":"2026-03-01",
                 "originalAmount":1.00,"referenceNote":"x"}""".formatted(customer))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));
        open("SUPPLIER", customer, "2026-03-31", "1.00", "x").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        mockMvc.perform(postJson("/api/v1/opening-obligations", """
                        {"purpose":"CUSTOMER","partyId":"%s","obligationDate":"2026-03-31","originalAmount":1.00,
                         "referenceNote":"x"}""".formatted(customer))
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from opening_obligations", Integer.class)).isEqualTo(2);
    }

    private ResultActions open(String purpose, UUID party, String date, String amount, String note) throws Exception {
        return asOwner(postJson("/api/v1/opening-obligations", """
                {"purpose":"%s","partyId":"%s","obligationDate":"%s","originalAmount":%s,"referenceNote":"%s"}"""
                .formatted(purpose, party, date, amount, note)));
    }
}

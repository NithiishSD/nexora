package com.nexora.finance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class CustomerOutstandingApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-17 outstanding confirmed orders minus allocations")
    void outstanding_confirmedOrdersMinusAllocations() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        confirmedOrder(customer, "KORA_WARP", "20.000", "1000.00");
        String b = confirmedOrder(customer, "KORA_WARP", "30.000", "1000.00");
        confirmedOrder(customer, "KORA_WARP", "40.000", "1000.00");
        createOrder(customer, "KORA_WARP", "10.000", "1000.00");                          // AC2: PLACED, not owed
        pay(customer, "45000.00", cash);

        // AC1: 90000 owed - 45000 paid = 45000; the settled order is gone, B shows 5000, C 40000
        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.id").value(customer.toString()))
                .andExpect(jsonPath("$.totalOutstanding").value(45000.00))
                .andExpect(jsonPath("$.advance").value(0.0))
                .andExpect(jsonPath("$.asOf").value(today().toString()))
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.orders[0].orderId").value(b))
                .andExpect(jsonPath("$.orders[0].amount").value(30000.00))
                .andExpect(jsonPath("$.orders[0].allocated").value(25000.00))
                .andExpect(jsonPath("$.orders[0].outstanding").value(5000.00))
                .andExpect(jsonPath("$.orders[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.orders[1].outstanding").value(40000.00));
        // an advance is shown beside the outstanding, never subtracted silently: pay 50000 more -> 5000 advance
        pay(customer, "50000.00", cash);
        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(jsonPath("$.totalOutstanding").value(0.0))
                .andExpect(jsonPath("$.advance").value(5000.00))
                .andExpect(jsonPath("$.orders.length()").value(0));

        // AC3: not a customer -> 422; unknown -> 404; STAFF has no PAYMENT_VIEW
        asOwner(get("/api/v1/customers/" + data.supplier().id() + "/outstanding"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        asOwner(get("/api/v1/customers/" + UUID.randomUUID() + "/outstanding")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/customers/" + customer + "/outstanding")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-PAY-18 outstanding groups by age buckets")
    void outstanding_groupsByAgeBuckets() throws Exception {
        UUID customer = data.customer().id();
        // AC1: 10000 from 40 days ago, 5000 from 5 days ago, 2000 from 100 days ago
        confirmed(customer, today().minusDays(40), "10.000");
        confirmed(customer, today().minusDays(5), "5.000");
        confirmed(customer, today().minusDays(100), "2.000");
        // AC2: a cancelled order is in no bucket
        String cancelled = orderOn(customer, today().minusDays(70), "9.000");
        asOwner(postJson("/api/v1/orders/" + cancelled + "/cancel", "{\"reason\":\"withdrawn\"}")).andExpect(status().isOk());

        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(jsonPath("$.ageing.days0to30").value(5000.00))
                .andExpect(jsonPath("$.ageing.days31to60").value(10000.00))
                .andExpect(jsonPath("$.ageing.days61to90").value(0.0))
                .andExpect(jsonPath("$.ageing.over90").value(2000.00))
                .andExpect(jsonPath("$.totalOutstanding").value(17000.00))
                .andExpect(jsonPath("$.orders[0].ageDays").value(100))
                .andExpect(jsonPath("$.orders[2].ageDays").value(5));
    }

    @Test
    @DisplayName("FR-PARTY-17 outstanding shows age without credit period alerts")
    void outstanding_showsAgeWithoutCreditPeriodAlerts() throws Exception {
        UUID customer = data.customer().id();
        confirmed(customer, today().minusDays(31), "1.000");

        // the age is shown; there is no due date, no overdue flag and no credit period anywhere in the response
        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(jsonPath("$.orders[0].ageDays").value(31))
                .andExpect(jsonPath("$.orders[0].dueDate").doesNotExist())
                .andExpect(jsonPath("$.orders[0].overdue").doesNotExist())
                .andExpect(jsonPath("$.creditDays").doesNotExist());
        asOwner(get("/api/v1/parties/" + customer))
                .andExpect(jsonPath("$.defaultCreditDays").value(Matchers.nullValue()));
    }

    private void confirmed(UUID customer, LocalDate date, String kg) throws Exception {
        String id = orderOn(customer, date, kg);
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());
    }

    // An order dated in the past (rate 1000.00 per kg).
    private String orderOn(UUID customer, LocalDate date, String kg) throws Exception {
        return body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","items":[{"productType":"KORA_WARP","requiredWeightKg":%s,
                 "ratePerKg":1000.00}]}""".formatted(customer, date, kg))).andExpect(status().isCreated()))
                .get("id").asString();
    }

    private void pay(UUID customer, String amount, String account) throws Exception {
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":%s,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(customer, amount, account))).andExpect(status().isCreated());
    }
}

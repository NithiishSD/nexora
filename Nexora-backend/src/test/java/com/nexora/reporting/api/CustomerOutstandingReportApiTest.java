package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class CustomerOutstandingReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-03 report sorted by outstanding")
    void report_sortedByOutstanding() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "0.00");
        // A owes 45000 on two orders (the oldest 40 days old), B 12000, C 5000; D has only a 2000 advance
        UUID a = data.customer().id();
        owedOrder(a, today().minusDays(40), "20000.00");
        owedOrder(a, today().minusDays(10), "25000.00");
        UUID b = data.customer().id();
        owedOrder(b, today().minusDays(5), "12000.00");
        UUID c = data.customer().id();
        owedOrder(c, today(), "5000.00");
        UUID d = data.customer().id();
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":2000.00,"paymentMethod":"UPI","financialAccountId":"%s"}"""
                .formatted(d, bank))).andExpect(status().isCreated());

        // default: everyone with an outstanding or an advance, largest outstanding first; totals over all rows
        asOwner(get("/api/v1/reports/customer-outstanding")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(4))
                .andExpect(jsonPath("$.rows[0].customer.id").value(a.toString()))
                .andExpect(jsonPath("$.rows[0].outstanding").value(45000.00))
                .andExpect(jsonPath("$.rows[0].unpaidOrders").value(2))
                .andExpect(jsonPath("$.rows[0].oldestAgeDays").value(40))
                .andExpect(jsonPath("$.rows[1].customer.id").value(b.toString()))
                .andExpect(jsonPath("$.rows[2].customer.id").value(c.toString()))
                .andExpect(jsonPath("$.rows[3].customer.id").value(d.toString()))
                .andExpect(jsonPath("$.rows[3].advance").value(2000.00))
                .andExpect(jsonPath("$.rows[3].oldestAgeDays").doesNotExist())
                .andExpect(jsonPath("$.totals.outstanding").value(62000.00))
                .andExpect(jsonPath("$.totals.advance").value(2000.00))
                .andExpect(jsonPath("$.totalElements").value(4));

        // AC1: minOutstanding=10000 keeps only A and B
        asOwner(get("/api/v1/reports/customer-outstanding?minOutstanding=10000")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].outstanding").value(45000.00))
                .andExpect(jsonPath("$.rows[1].outstanding").value(12000.00))
                .andExpect(jsonPath("$.totals.outstanding").value(57000.00));
        // ascending, and ages measured to another date
        asOwner(get("/api/v1/reports/customer-outstanding?minOutstanding=1&sort=outstanding,asc&asOf="
                + today().plusDays(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].customer.id").value(c.toString()))
                .andExpect(jsonPath("$.rows[0].oldestAgeDays").value(1));
        // paging keeps the totals of every row
        asOwner(get("/api/v1/reports/customer-outstanding?size=1&page=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].customer.id").value(b.toString()))
                .andExpect(jsonPath("$.totals.outstanding").value(62000.00))
                .andExpect(jsonPath("$.totalPages").value(4));

        // AC2: an unknown sort field is 400; STAFF has no REPORT_VIEW
        asOwner(get("/api/v1/reports/customer-outstanding?sort=unknownField")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/reports/customer-outstanding").header(HttpHeaders.AUTHORIZATION,
                AuthTokens.bearer(tokens.staff()))).andExpect(status().isForbidden());
    }

    // A confirmed one-line order (1 kg at the amount) dated in the past, so it is owed.
    private void owedOrder(UUID customer, LocalDate date, String amount) throws Exception {
        String id = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s",
                 "items":[{"productType":"RAW_SILK","requiredWeightKg":1.000,"ratePerKg":%s}]}"""
                .formatted(customer, date, amount))).andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());
    }
}

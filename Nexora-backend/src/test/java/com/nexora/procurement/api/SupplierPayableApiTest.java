package com.nexora.procurement.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class SupplierPayableApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PROC-15 FR-PAY-19 payable is accepted minus returned times rate minus allocations")
    void payable_isAcceptedMinusReturnedTimesRateMinusAllocations() throws Exception {
        UUID supplier = data.supplier().id();
        String cash = createAccount("Cash", "CASH", "100000.00");
        String id = purchase(supplier, today().toString(), 30, "100.000", "800.00");
        // AC3: nothing received yet -> payable 0.00
        asOwner(get("/api/v1/purchases/" + id)).andExpect(jsonPath("$.payableAmount").value(0.0))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.overdue").value(false));
        // AC1: 100 kg received, 5 rejected -> 95 accepted; 15 returned -> (95 - 15) x 800 = 64000 (rejected not paid)
        receive(id, "100.000", "95.000", "5.000").andExpect(status().isCreated());
        asOwner(postWithKey("/api/v1/purchases/" + id + "/returns", """
                {"returnDate":"%s","returnedWeightKg":15.000,"reason":"knots"}""".formatted(today())))
                .andExpect(status().isCreated());
        asOwner(get("/api/v1/purchases/" + id)).andExpect(jsonPath("$.payableAmount").value(64000.00))
                .andExpect(jsonPath("$.outstandingAmount").value(64000.00));

        // AC2: 50000 allocated -> 14000 remaining, PARTIALLY_PAID, on the purchase and the supplier's payables
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"SUPPLIER","partyId":"%s","amount":50000.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(supplier, cash))).andExpect(status().isCreated());
        asOwner(get("/api/v1/purchases/" + id)).andExpect(jsonPath("$.allocatedAmount").value(50000.00))
                .andExpect(jsonPath("$.outstandingAmount").value(14000.00))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));
        asOwner(get("/api/v1/suppliers/" + supplier + "/payables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPayable").value(14000.00))
                .andExpect(jsonPath("$.totalOverdue").value(0.0))
                .andExpect(jsonPath("$.purchases.length()").value(1))
                .andExpect(jsonPath("$.purchases[0].payable").value(64000.00))
                .andExpect(jsonPath("$.purchases[0].allocated").value(50000.00))
                .andExpect(jsonPath("$.purchases[0].outstanding").value(14000.00))
                .andExpect(jsonPath("$.purchases[0].paymentStatus").value("PARTIALLY_PAID"));
        asOwner(get("/api/v1/purchases?paymentStatus=PARTIALLY_PAID"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].outstandingAmount").value(14000.00));
        asOwner(get("/api/v1/purchases?paymentStatus=PAID")).andExpect(jsonPath("$.totalElements").value(0));

        // FR-PAY-19 AC2: not a supplier -> 422; STAFF cannot read payables
        asOwner(get("/api/v1/suppliers/" + data.customer().id() + "/payables"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        mockMvc.perform(get("/api/v1/suppliers/" + supplier + "/payables")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-PROC-16 purchase past due with payable is overdue")
    void purchase_pastDueWithPayable_isOverdue() throws Exception {
        UUID supplier = data.supplier().id();
        String cash = createAccount("Cash", "CASH", "100000.00");
        // AC1: due 3 days ago with 5000 to pay -> overdue, 3 days
        String late = purchase(supplier, today().minusDays(10).toString(), 7, "10.000", "500.00");
        receive(late, "10.000", "10.000", "0.000").andExpect(status().isCreated());
        // due in 5 days: not overdue, but due within 7 days
        String soon = purchase(supplier, today().toString(), 5, "4.000", "500.00");
        receive(soon, "4.000", "4.000", "0.000").andExpect(status().isCreated());

        asOwner(get("/api/v1/purchases/" + late)).andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.overdueDays").value(3));
        asOwner(get("/api/v1/purchases/" + soon)).andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.overdueDays").value(0));
        asOwner(get("/api/v1/purchases?overdue=true")).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(late))
                .andExpect(jsonPath("$.content[0].overdue").value(true));
        asOwner(get("/api/v1/payables/suppliers"))
                .andExpect(jsonPath("$.totalPayable").value(7000.00))
                .andExpect(jsonPath("$.totalOverdue").value(5000.00))
                .andExpect(jsonPath("$.dueWithin7Days").value(2000.00))
                .andExpect(jsonPath("$.suppliers[0].oldestDueDate").value(today().minusDays(3).toString()));
        asOwner(get("/api/v1/payables/suppliers?sort=name")).andExpect(status().isBadRequest());

        // AC2: fully paid -> no longer overdue (oldest purchase is paid first)
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"SUPPLIER","partyId":"%s","amount":5000.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(supplier, cash))).andExpect(status().isCreated());
        asOwner(get("/api/v1/purchases/" + late)).andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
        asOwner(get("/api/v1/payables/suppliers?overdueOnly=true")).andExpect(jsonPath("$.suppliers.length()").value(0));

        // AC3: a cancelled purchase past its due date is never overdue
        String cancelled = purchase(supplier, today().minusDays(10).toString(), 1, "1.000", "500.00");
        asOwner(postJson("/api/v1/purchases/" + cancelled + "/cancel", "{\"reason\":\"not needed\"}"))
                .andExpect(status().isOk());
        asOwner(get("/api/v1/purchases/" + cancelled)).andExpect(jsonPath("$.overdue").value(false));
    }

    private String purchase(UUID supplier, String date, int creditDays, String kg, String rate) throws Exception {
        return body(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":%s,"ratePerKg":%s,"paymentType":"CREDIT",
                 "creditDays":%d}""".formatted(supplier, date, kg, rate, creditDays)))
                .andExpect(status().isCreated())).get("id").asString();
    }
}

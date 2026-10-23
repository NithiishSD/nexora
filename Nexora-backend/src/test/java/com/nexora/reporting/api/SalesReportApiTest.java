package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.parties.domain.PartyRole;
import com.nexora.support.AbstractApiTest;

class SalesReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-07 report revenue recognised at delivery")
    void report_revenueRecognisedAtDelivery() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "0.00");
        data.rawSilkInStock("300.000");
        UUID anand = data.party("Anand", List.of(PartyRole.CUSTOMER), null, null).id();
        UUID bala = data.party("Bala", List.of(PartyRole.CUSTOMER), null, null).id();
        // Anand: 100 kg at 850 ordered today and delivered (85000); an older order (40 days ago) of 20 kg at 900
        // delivered today: revenue now, but not placed in the period. 30000 paid settles the older order first
        // (18000, oldest first), so the order placed in the period still owes 85000 - 12000 = 73000
        deliveredOrder(anand, today(), "100.000", "850.00");
        deliveredOrder(anand, today().minusDays(40), "20.000", "900.00");
        pay(anand, "30000.00", bank);
        // Bala: a PLACED order of 10000 never delivered (AC3) and a 40000 advance; a loan of 100000 arrives (AC2)
        createOrder(bala, "VUDA_WARP", "10.000", "1000.00");
        pay(bala, "40000.00", bank);
        asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":100000.00,"startDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(data.lender().id(), today(), bank))).andExpect(status().isCreated());

        String period = "?from=" + today().minusDays(10) + "&to=" + today();
        // AC1: revenue = the item amounts delivered in the period (85000 + 18000); AC2/AC3: the loan, the advance and
        // the undelivered order add nothing to it
        asOwner(get("/api/v1/reports/sales" + period)).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("MONTH"))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].period").value(YearMonth.from(today()).toString()))
                .andExpect(jsonPath("$.rows[0].orderValuePlaced").value(95000.00))
                .andExpect(jsonPath("$.rows[0].revenueDelivered").value(103000.00))
                .andExpect(jsonPath("$.rows[0].kgDelivered").value(120.000))
                .andExpect(jsonPath("$.rows[0].outstanding").value(73000.00))
                .andExpect(jsonPath("$.totals.revenueDelivered").value(103000.00));
        // per customer (by name) and per product
        asOwner(get("/api/v1/reports/sales" + period + "&groupBy=CUSTOMER")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].customer.id").value(anand.toString()))
                .andExpect(jsonPath("$.rows[0].revenueDelivered").value(103000.00))
                .andExpect(jsonPath("$.rows[1].customer.id").value(bala.toString()))
                .andExpect(jsonPath("$.rows[1].orderValuePlaced").value(10000.00))
                .andExpect(jsonPath("$.rows[1].revenueDelivered").value(0.0))
                .andExpect(jsonPath("$.rows[1].outstanding").value(0.0))
                .andExpect(jsonPath("$.rows[0].period").doesNotExist());
        asOwner(get("/api/v1/reports/sales" + period + "&groupBy=PRODUCT&customerId=" + anand))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].productType").value("RAW_SILK"));

        // errors: unknown groupBy 400 MALFORMED_REQUEST; from after to 400 VALIDATION_FAILED
        asOwner(get("/api/v1/reports/sales" + period + "&groupBy=YEAR")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        asOwner(get("/api/v1/reports/sales?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("FR-RPT-17 report has no profit figures in first release")
    void report_hasNoProfitFiguresInFirstRelease() throws Exception {
        data.rawSilkInStock("10.000");
        deliveredOrder(data.customer().id(), today(), "10.000", "900.00");
        String period = "?from=" + today() + "&to=" + today();
        asOwner(get("/api/v1/reports/sales" + period)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].revenueDelivered").value(9000.00))
                .andExpect(jsonPath("$.rows[0].profit").doesNotExist())
                .andExpect(jsonPath("$.rows[0].cost").doesNotExist())
                .andExpect(jsonPath("$.totals.profit").doesNotExist());
        // post-MVP profit reports are now offered (M18)
        asOwner(get("/api/v1/reports/profit-and-loss" + period)).andExpect(status().isOk());
        asOwner(get("/api/v1/reports/profitability" + period)).andExpect(status().isOk());
    }

    // A raw-silk order dated `date`, confirmed, readied from stock and delivered today.
    private void deliveredOrder(UUID customer, LocalDate date, String kg, String rate) throws Exception {
        String id = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s",
                 "items":[{"productType":"RAW_SILK","requiredWeightKg":%s,"ratePerKg":%s}]}"""
                .formatted(customer, date, kg, rate))).andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());
        asOwner(postJson("/api/v1/orders/" + id + "/ready", "")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"%s\"}".formatted(today())))
                .andExpect(status().isOk());
    }

    private void pay(UUID customer, String amount, String bank) throws Exception {
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":%s,"paymentMethod":"UPI","financialAccountId":"%s"}"""
                .formatted(customer, amount, bank))).andExpect(status().isCreated());
    }
}

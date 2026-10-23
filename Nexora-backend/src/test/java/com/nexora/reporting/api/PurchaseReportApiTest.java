package com.nexora.reporting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import com.nexora.support.AbstractApiTest;

class PurchaseReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-06 report grouped by supplier and product")
    void report_groupedBySupplierAndProduct() throws Exception {
        LocalDate from = today().minusDays(30);
        String a = data.supplier().id().toString();
        // supplier A in the period: raw silk 100 kg at 800 (60 received, 58 accepted, 2 rejected) and 50 kg at 700
        // (nothing received); vuda warp 20 kg at 500, all accepted, 5 kg returned
        String raw = purchase(a, "RAW_SILK", today().minusDays(10), "100.000", "800.00");
        receive(raw, "60.000", "58.000", "2.000").andExpect(status().isCreated());
        purchase(a, "RAW_SILK", today().minusDays(5), "50.000", "700.00");
        String vuda = purchase(a, "VUDA_WARP", today().minusDays(3), "20.000", "500.00");
        receive(vuda, "20.000", "20.000", "0.000").andExpect(status().isCreated());
        asOwner(postWithKey("/api/v1/purchases/" + vuda + "/returns", """
                {"returnDate":"%s","returnedWeightKg":5.000,"reason":"damaged"}""".formatted(today())))
                .andExpect(status().isCreated());
        // supplier B: one purchase before the period, one cancelled in it: neither counts
        String b = data.supplier().id().toString();
        purchase(b, "RAW_SILK", today().minusDays(40), "10.000", "800.00");
        String cancelled = purchase(b, "RAW_SILK", today(), "10.000", "800.00");
        asOwner(postJson("/api/v1/purchases/" + cancelled + "/cancel", "{\"reason\":\"not needed\"}"))
                .andExpect(status().isOk());

        String period = "?from=" + from + "&to=" + today();
        JsonNode report = body(asOwner(get("/api/v1/reports/purchases" + period)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].productType").value("RAW_SILK"))
                .andExpect(jsonPath("$.rows[0].purchases").value(2))
                .andExpect(jsonPath("$.rows[0].orderedKg").value(150.000))
                .andExpect(jsonPath("$.rows[0].receivedKg").value(60.000))
                .andExpect(jsonPath("$.rows[0].acceptedKg").value(58.000))
                .andExpect(jsonPath("$.rows[0].rejectedKg").value(2.000))
                .andExpect(jsonPath("$.rows[0].orderedValue").value(115000.00))      // 80000 + 35000
                .andExpect(jsonPath("$.rows[0].payableValue").value(46400.00))       // 58 x 800 (Q1)
                .andExpect(jsonPath("$.rows[1].productType").value("VUDA_WARP"))
                .andExpect(jsonPath("$.rows[1].returnedKg").value(5.000))
                .andExpect(jsonPath("$.rows[1].payableValue").value(7500.00))        // (20 - 5) x 500
                .andExpect(jsonPath("$.totals.purchases").value(3))
                .andExpect(jsonPath("$.totals.orderedKg").value(170.000))
                .andExpect(jsonPath("$.totals.payableValue").value(53900.00)));

        // AC1: the supplier's totals match the purchase list for the same period (cancelled ones excluded)
        JsonNode list = body(asOwner(get("/api/v1/purchases" + period + "&supplierId=" + a)));
        BigDecimal listed = BigDecimal.ZERO;
        for (JsonNode p : list.get("content")) {
            listed = listed.add(p.get("orderedWeightKg").decimalValue());
        }
        assertThat(report.get("totals").get("orderedKg").decimalValue()).isEqualByComparingTo(listed);
        // filters: one product
        asOwner(get("/api/v1/reports/purchases" + period + "&productType=VUDA_WARP"))
                .andExpect(jsonPath("$.rows.length()").value(1)).andExpect(jsonPath("$.totals.orderedKg").value(20.000));

        // AC2: an invalid productType is 400 MALFORMED_REQUEST; from after to is 400 VALIDATION_FAILED
        asOwner(get("/api/v1/reports/purchases" + period + "&productType=COTTON")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        asOwner(get("/api/v1/reports/purchases?from=" + today() + "&to=" + from)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String purchase(String supplier, String product, LocalDate date, String kg, String rate) throws Exception {
        return body(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","productType":"%s","purchaseDate":"%s","orderedWeightKg":%s,"ratePerKg":%s,
                 "paymentType":"CREDIT"}""".formatted(supplier, product, date, kg, rate)))
                .andExpect(status().isCreated())).get("id").asString();
    }
}

package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

class ProductionReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-10 FR-PROD-12 report shows wastage percentage per batch")
    void report_showsWastagePercentagePerBatch() throws Exception {
        data.rawSilkInStock("300.000");
        // batch 1: 100 in, 94 out, 4 wastage, 2 discrepancy -> 4.00 %; batch 2: 60 in, 57 out, 3 wastage -> 5.00 %
        String first = startedBatch("KORA_WARP", "100.000");
        complete(first, "94.000", "4.000", "2.000");
        String second = startedBatch("SILK_WARP", "60.000");
        complete(second, "57.000", "3.000", "0.000");
        // AC3: an IN_PROGRESS batch is not counted
        startedBatch("KORA_WARP", "50.000");

        String period = "?from=" + today() + "&to=" + today();
        // AC1 and FR-PROD-12 AC1: wastage percentage per batch; totals weighted by input (7 / 160 = 4.38 %)
        asOwner(get("/api/v1/reports/production" + period)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].inputKg").value(100.000))
                .andExpect(jsonPath("$.rows[0].outputKg").value(94.000))
                .andExpect(jsonPath("$.rows[0].wastageKg").value(4.000))
                .andExpect(jsonPath("$.rows[0].discrepancyKg").value(2.000))
                .andExpect(jsonPath("$.rows[0].wastagePercent").value(4.00))
                .andExpect(jsonPath("$.rows[1].wastagePercent").value(5.00))
                .andExpect(jsonPath("$.totals.batches").value(2))
                .andExpect(jsonPath("$.totals.inputKg").value(160.000))
                .andExpect(jsonPath("$.totals.wastageKg").value(7.000))
                .andExpect(jsonPath("$.totals.wastagePercent").value(4.38));
        asOwner(get("/api/v1/reports/production" + period + "&productType=SILK_WARP"))
                .andExpect(jsonPath("$.rows.length()").value(1)).andExpect(jsonPath("$.totals.inputKg").value(60.000));

        // AC2 and FR-PROD-12 AC2: a period with no completed batches is 200 with zero totals
        asOwner(get("/api/v1/reports/production?from=" + today().minusDays(30) + "&to=" + today().minusDays(20)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(0))
                .andExpect(jsonPath("$.totals.inputKg").value(0.0))
                .andExpect(jsonPath("$.totals.wastagePercent").value(0.0));
        asOwner(get("/api/v1/reports/production?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String startedBatch(String product, String kg) throws Exception {
        String id = body(asOwner(postJson("/api/v1/production-batches", """
                {"productType":"%s","productionDate":"%s","inputWeightKg":%s}""".formatted(product, today(), kg)))
                .andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/production-batches/" + id + "/start", "{}")).andExpect(status().isOk());
        return id;
    }

    private void complete(String batch, String output, String wastage, String discrepancy) throws Exception {
        asOwner(postJson("/api/v1/production-batches/" + batch + "/complete", """
                {"outputWeightKg":%s,"wastageWeightKg":%s,"discrepancyWeightKg":%s}"""
                .formatted(output, wastage, discrepancy))).andExpect(status().isOk());
    }
}

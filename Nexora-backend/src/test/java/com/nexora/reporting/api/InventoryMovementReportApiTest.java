package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

class InventoryMovementReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-09 report opening movements closing")
    void report_openingMovementsClosing() throws Exception {
        // before the period: 100 kg raw silk received 5 days ago
        String supplier = data.supplier().id().toString();
        String old = body(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":100.000,"ratePerKg":800.00,
                 "paymentType":"CREDIT"}""".formatted(supplier, today().minusDays(10))))
                .andExpect(status().isCreated())).get("id").asString();
        asOwner(postWithKey("/api/v1/purchases/" + old + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":100.000,"acceptedWeightKg":100.000,"rejectedWeightKg":0.000}"""
                .formatted(today().minusDays(5)))).andExpect(status().isCreated());
        // in the period: 50 kg received, 30 kg consumed into a kora warp batch
        String recent = body(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":50.000,"ratePerKg":800.00,
                 "paymentType":"CREDIT"}""".formatted(supplier, today()))).andExpect(status().isCreated()))
                .get("id").asString();
        asOwner(postWithKey("/api/v1/purchases/" + recent + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":50.000,"acceptedWeightKg":50.000,"rejectedWeightKg":0.000}"""
                .formatted(today()))).andExpect(status().isCreated());
        String batch = body(asOwner(postJson("/api/v1/production-batches", """
                {"productType":"KORA_WARP","productionDate":"%s","inputWeightKg":30.000}""".formatted(today())))
                .andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/production-batches/" + batch + "/start", "{}")).andExpect(status().isOk());

        String period = "?from=" + today().minusDays(2) + "&to=" + today();
        // AC1: opening 100.000 + receipts 50.000 - consumption 30.000 = closing 120.000 (raw silk is only in RAW_STOCK)
        asOwner(get("/api/v1/reports/inventory-movements" + period + "&productType=RAW_SILK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].location").value("RAW_STOCK"))
                .andExpect(jsonPath("$[0].openingKg").value(100.000))
                .andExpect(jsonPath("$[0].movementsByType[?(@.movementType == 'RECEIPT')].inKg").value(50.0))
                .andExpect(jsonPath("$[0].movementsByType[?(@.movementType == 'CONSUMPTION')].outKg").value(30.0))
                .andExpect(jsonPath("$[0].closingKg").value(120.000));
        // the closing equals the stock summary (FR-INV-08 AC3)
        asOwner(get("/api/v1/inventory/summary")).andExpect(jsonPath("$.rawSilkAvailableKg").value(120.000));
        // the warp side: 30 kg arrived in INTERNAL_WIP as kora warp; one location asked for
        asOwner(get("/api/v1/reports/inventory-movements" + period + "&productType=KORA_WARP&location=INTERNAL_WIP"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].openingKg").value(0.0))
                .andExpect(jsonPath("$[0].movementsByType[0].movementType").value("CONSUMPTION"))
                .andExpect(jsonPath("$[0].movementsByType[0].inKg").value(30.000))
                .andExpect(jsonPath("$[0].closingKg").value(30.000));

        // AC2: an unknown location is 400 MALFORMED_REQUEST; an outside location 400 VALIDATION_FAILED;
        // productType is required
        asOwner(get("/api/v1/reports/inventory-movements" + period + "&productType=RAW_SILK&location=MOON"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        asOwner(get("/api/v1/reports/inventory-movements" + period + "&productType=RAW_SILK&location=CUSTOMER"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        asOwner(get("/api/v1/reports/inventory-movements" + period)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-RPT-09 FR-INV-13 report includes opening stock entered at go-live")
    void report_includesOpeningStock() throws Exception {
        // an opening balance has no source location; it must count as stock arriving, not break the report
        asOwner(postJson("/api/v1/inventory/opening-balances", """
                {"productType":"RAW_SILK","location":"RAW_STOCK","quantityKg":200.000,"movementDate":"%s"}"""
                .formatted(today().minusDays(20)))).andExpect(status().isCreated());
        asOwner(get("/api/v1/reports/inventory-movements?from=" + today().minusDays(30) + "&to=" + today()
                + "&productType=RAW_SILK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].openingKg").value(0.0))
                .andExpect(jsonPath("$[0].movementsByType[0].movementType").value("OPENING_BALANCE"))
                .andExpect(jsonPath("$[0].movementsByType[0].inKg").value(200.000))
                .andExpect(jsonPath("$[0].closingKg").value(200.000));
        asOwner(get("/api/v1/reports/inventory-movements?from=" + today().minusDays(5) + "&to=" + today()
                + "&productType=RAW_SILK")).andExpect(jsonPath("$[0].openingKg").value(200.000));
    }
}

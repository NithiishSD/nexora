package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

class SupplierPayablesReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-05 report with ageing buckets")
    void report_withAgeingBuckets() throws Exception {
        // supplier A: not due (1000), 3 days overdue (2000), 10 days overdue (3000, AC1: 8-15), 20 days overdue (4000)
        String a = data.supplier().id().toString();
        String notDue = receivedPurchase(a, today(), 30, "1000.00");
        receivedPurchase(a, today().minusDays(10), 7, "2000.00");
        String tenDays = receivedPurchase(a, today().minusDays(17), 7, "3000.00");
        receivedPurchase(a, today().minusDays(27), 7, "4000.00");
        // supplier B: an opening payable of 500 due 2 days ago; a cancelled purchase owes nothing
        String b = data.supplier().id().toString();
        asOwner(postJson("/api/v1/opening-obligations", """
                {"purpose":"SUPPLIER","partyId":"%s","obligationDate":"%s","dueDate":"%s","originalAmount":500.00,
                 "referenceNote":"old book p.12"}""".formatted(b, today().minusDays(30), today().minusDays(2))))
                .andExpect(status().isCreated());
        String cancelled = createPurchase(UUID.fromString(b), "RAW_SILK", "10.000", "100.00");
        asOwner(postJson("/api/v1/purchases/" + cancelled + "/cancel", "{\"reason\":\"not needed\"}"))
                .andExpect(status().isOk());

        asOwner(get("/api/v1/reports/supplier-payables")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10500.00))
                .andExpect(jsonPath("$.buckets.NOT_DUE").value(1000.00))
                .andExpect(jsonPath("$.buckets.D1_7").value(2500.00))
                .andExpect(jsonPath("$.buckets.D8_15").value(3000.00))
                .andExpect(jsonPath("$.buckets.OVER_15").value(4000.00))
                .andExpect(jsonPath("$.suppliers.length()").value(2))
                .andExpect(jsonPath("$.suppliers[0].supplier.id").value(a))
                .andExpect(jsonPath("$.suppliers[0].total").value(10000.00))
                .andExpect(jsonPath("$.suppliers[0].purchases.length()").value(4))
                .andExpect(jsonPath("$.suppliers[0].purchases[0].overdueDays").value(20))
                .andExpect(jsonPath("$.suppliers[0].purchases[0].bucket").value("OVER_15"))
                .andExpect(jsonPath("$.suppliers[0].purchases[1].purchaseId").value(tenDays))
                .andExpect(jsonPath("$.suppliers[0].purchases[1].overdueDays").value(10))
                .andExpect(jsonPath("$.suppliers[0].purchases[1].bucket").value("D8_15"))
                .andExpect(jsonPath("$.suppliers[0].purchases[3].purchaseId").value(notDue))
                .andExpect(jsonPath("$.suppliers[0].purchases[3].bucket").value("NOT_DUE"))
                .andExpect(jsonPath("$.suppliers[1].buckets.D1_7").value(500.00))
                .andExpect(jsonPath("$.suppliers[1].purchases[0].purchaseId").doesNotExist());
        // the total equals the supplier payables overview (E-PAY-08)
        asOwner(get("/api/v1/payables/suppliers")).andExpect(jsonPath("$.totalPayable").value(10500.00));

        // filters: one supplier; purchase date within the last 12 days
        asOwner(get("/api/v1/reports/supplier-payables?supplierId=" + b)).andExpect(status().isOk())
                .andExpect(jsonPath("$.suppliers.length()").value(1)).andExpect(jsonPath("$.total").value(500.00));
        asOwner(get("/api/v1/reports/supplier-payables?from=" + today().minusDays(12) + "&to=" + today()))
                .andExpect(jsonPath("$.total").value(3000.00));

        // AC2: from after to is 400; an unknown supplier 404
        asOwner(get("/api/v1/reports/supplier-payables?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        asOwner(get("/api/v1/reports/supplier-payables?supplierId=" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // 10 kg on credit at a tenth of the amount, all received and accepted; returns its id.
    private String receivedPurchase(String supplier, LocalDate date, int creditDays, String amount) throws Exception {
        String rate = new java.math.BigDecimal(amount).movePointLeft(1).setScale(2).toPlainString();
        String id = body(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":10.000,"ratePerKg":%s,"paymentType":"CREDIT",
                 "creditDays":%d}""".formatted(supplier, date, rate, creditDays))).andExpect(status().isCreated()))
                .get("id").asString();
        asOwner(postWithKey("/api/v1/purchases/" + id + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":10.000,"acceptedWeightKg":10.000,"rejectedWeightKg":0.000}"""
                .formatted(today()))).andExpect(status().isCreated());
        return id;
    }
}

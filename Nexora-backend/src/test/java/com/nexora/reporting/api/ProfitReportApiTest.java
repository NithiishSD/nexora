package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.parties.domain.PartyRole;
import com.nexora.support.AbstractApiTest;

class ProfitReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-17 E-RPT-15 profit and loss calculates revenue cost and profit")
    void profitAndLoss_calculatesRevenueCostAndProfit() throws Exception {
        String bank = createAccount("HDFC Bank", "BANK", "500000.00");
        String cash = createAccount("Cash Box", "CASH", "100000.00");

        // 1. Supplier and Raw silk purchase: 100 kg @ 1000.00 = 100,000
        String supplier = data.party("Silk Supplier", List.of(PartyRole.SUPPLIER), null, null).id().toString();
        String purchase = purchase(supplier, today(), "100.000", "1000.00");
        receive(purchase, today(), "100.000");

        // 2. Customer and Delivered order: 50 kg RAW_SILK @ 1500.00 = 75,000
        String customer = data.party("Kalyan Silks", List.of(PartyRole.CUSTOMER), null, null).id().toString();
        deliveredOrder(customer, today(), "50.000", "1500.00");

        // 3. Operating expense: 5000 Business + 2000 Personal (personal must be excluded)
        asOwner(postWithKey("/api/v1/expenses", """
                {"category":"Electricity","expenseKind":"BUSINESS","recurrence":"MONTHLY",
                 "amount":5000.00,"expenseDate":"%s","financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(today(), bank))).andExpect(status().isCreated());
        asOwner(postWithKey("/api/v1/expenses", """
                {"category":"OTHER","expenseKind":"PERSONAL","recurrence":"ONE_TIME",
                 "amount":2000.00,"expenseDate":"%s","financialAccountId":"%s","paymentMethod":"CASH"}"""
                .formatted(today(), cash))).andExpect(status().isCreated());

        // 4. Loan interest payment: 1000
        String lender = data.party("City Bank", List.of(PartyRole.LENDER), null, null).id().toString();
        String loan = body(asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanNumber":"LON-P-001","loanType":"BANK","principalAmount":50000.00,
                 "startDate":"%s","financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(lender, today().minusMonths(1), bank)))
                .andExpect(status().isCreated())).get("id").asString();
        asOwner(postWithKey("/api/v1/loans/" + loan + "/interest-payments", """
                {"paymentDate":"%s","amount":1000.00,"financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(today(), bank))).andExpect(status().isCreated());

        // 5. Worker wage: 2000
        String worker = data.party("Ramu", List.of(PartyRole.WORKER), null, null).id().toString();
        asOwner(postJson("/api/v1/work-records", """
                {"workerId":"%s","workDate":"%s","workType":"ROLLING","hours":20.00,"rate":100.00}"""
                .formatted(worker, today()))).andExpect(status().isCreated());

        // 6. Outsourcing job: 30 kg @ 50.00 = 1500
        String mfg = data.party("Arun Weavers", List.of(PartyRole.MANUFACTURER), null, null).id().toString();
        String job = body(asOwner(postJson("/api/v1/outsourcing-jobs", """
                {"manufacturerId":"%s","productType":"SILK_WARP","issuedWeightKg":30.000,"ratePerKg":50.00}"""
                .formatted(mfg))).andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/outsourcing-jobs/" + job + "/issue", "{}")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/outsourcing-jobs/" + job + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":30.000,"wastageWeightKg":0.000}""".formatted(today())))
                .andExpect(status().isOk());

        String range = "?from=" + today().withDayOfMonth(1) + "&to=" + today();
        asOwner(get("/api/v1/reports/profit-and-loss" + range)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revenue").value(75000.00))
                .andExpect(jsonPath("$[0].businessExpenses").value(5000.00))
                .andExpect(jsonPath("$[0].interestCost").value(1000.00))
                .andExpect(jsonPath("$[0].productionCost").value(103500.00)) // 100k purchases + 2k wages + 1.5k job
                .andExpect(jsonPath("$[0].profit").value(-34500.00));

        // AC2: Empty month returns 200 with zero values
        LocalDate past = today().minusYears(2);
        asOwner(get("/api/v1/reports/profit-and-loss?from=" + past + "&to=" + past.plusDays(5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revenue").value(0.00))
                .andExpect(jsonPath("$[0].profit").value(0.00));

        // Rejection: from after to returns 400
        asOwner(get("/api/v1/reports/profit-and-loss?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("FR-RPT-17 FR-OUT-12 FR-PROD-12 E-RPT-16 profitability byProduct byCustomer byManufacturer")
    void profitability_byProduct_byCustomer_byManufacturer() throws Exception {
        String bank = createAccount("SBI Bank", "BANK", "300000.00");

        // 1. Purchase 100 kg RAW_SILK @ 1000.00
        String supplier = data.party("Silk Sup 2", List.of(PartyRole.SUPPLIER), null, null).id().toString();
        String purchase = purchase(supplier, today(), "100.000", "1000.00");
        receive(purchase, today(), "100.000");

        // 2. Deliver 50 kg RAW_SILK @ 1500.00 to Customer
        String customer = data.party("Kalyan Silks 2", List.of(PartyRole.CUSTOMER), null, null).id().toString();
        deliveredOrder(customer, today(), "50.000", "1500.00");

        // 3. Outsourcing job for Manufacturer: 40 kg @ 50.00 = 2000
        String mfg = data.party("Arun Weavers 2", List.of(PartyRole.MANUFACTURER), null, null).id().toString();
        String job = body(asOwner(postJson("/api/v1/outsourcing-jobs", """
                {"manufacturerId":"%s","productType":"SILK_WARP","issuedWeightKg":40.000,"ratePerKg":50.00}"""
                .formatted(mfg))).andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/outsourcing-jobs/" + job + "/issue", "{}")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/outsourcing-jobs/" + job + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":40.000,"wastageWeightKg":0.000}""".formatted(today())))
                .andExpect(status().isOk());

        String range = "?from=" + today().withDayOfMonth(1) + "&to=" + today();

        // PRODUCT dimension
        asOwner(get("/api/v1/reports/profitability" + range + "&dimension=PRODUCT")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'RAW_SILK')].revenue").value(75000.00))
                .andExpect(jsonPath("$[?(@.key == 'RAW_SILK')].cost").value(50000.00))
                .andExpect(jsonPath("$[?(@.key == 'RAW_SILK')].margin").value(25000.00))
                .andExpect(jsonPath("$[?(@.key == 'RAW_SILK')].marginPercent").value(33.33));

        // CUSTOMER dimension
        asOwner(get("/api/v1/reports/profitability" + range + "&dimension=CUSTOMER")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(customer))
                .andExpect(jsonPath("$[0].revenue").value(75000.00))
                .andExpect(jsonPath("$[0].cost").value(50000.00))
                .andExpect(jsonPath("$[0].margin").value(25000.00))
                .andExpect(jsonPath("$[0].marginPercent").value(33.33));

        // MANUFACTURER dimension
        asOwner(get("/api/v1/reports/profitability" + range + "&dimension=MANUFACTURER")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mfg))
                .andExpect(jsonPath("$[0].revenue").value(0.00))
                .andExpect(jsonPath("$[0].cost").value(2000.00))
                .andExpect(jsonPath("$[0].margin").value(-2000.00))
                .andExpect(jsonPath("$[0].marginPercent").value(0.00));

        // Rejection: from after to returns 400
        asOwner(get("/api/v1/reports/profitability?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String purchase(String supplier, LocalDate date, String kg, String rate) throws Exception {
        return body(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","productType":"RAW_SILK",
                 "orderedWeightKg":%s,"ratePerKg":%s,"paymentType":"CASH"}"""
                .formatted(supplier, date, kg, rate))).andExpect(status().isCreated())).get("id").asString();
    }

    private void receive(String purchaseId, LocalDate date, String kg) throws Exception {
        asOwner(postWithKey("/api/v1/purchases/" + purchaseId + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":%s,"acceptedWeightKg":%s,"rejectedWeightKg":0.000}"""
                .formatted(date, kg, kg))).andExpect(status().isCreated());
    }

    private void deliveredOrder(String customer, LocalDate date, String kg, String rate) throws Exception {
        String id = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s",
                 "items":[{"productType":"RAW_SILK","requiredWeightKg":%s,"ratePerKg":%s}]}"""
                .formatted(customer, date, kg, rate))).andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());
        asOwner(postJson("/api/v1/orders/" + id + "/ready", "")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"%s\"}".formatted(date)))
                .andExpect(status().isOk());
    }
}

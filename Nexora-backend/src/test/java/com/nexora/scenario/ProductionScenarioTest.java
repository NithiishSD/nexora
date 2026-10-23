package com.nexora.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;

// The in-house loop end to end through the API (NFR-TEST-06): buy raw silk, make kora warp in a batch that a worker
// warps, reconcile the batch with an explicit discrepancy, deliver to the customer, and settle customer, supplier and
// worker. Every figure is read back from the server, and the stock projection must equal the movement ledger.
class ProductionScenarioTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-TEST-06 purchase produce deliver pay")
    void purchaseProduceDeliverPay() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "200000.00");
        String cash = createAccount("Cash", "CASH", "5000.00");

        // 1. buy and receive 100 kg raw silk at 800.00 on credit
        String supplier = data.supplier().id().toString();
        String purchase = id(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":100.000,"ratePerKg":800.00,
                 "paymentType":"CREDIT"}""".formatted(supplier, today()))).andExpect(status().isCreated()));
        asOwner(postWithKey("/api/v1/purchases/" + purchase + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":100.000,"acceptedWeightKg":100.000,"rejectedWeightKg":0.000}"""
                .formatted(today()))).andExpect(status().isCreated());

        // 2. a customer orders 90 kg kora warp at 1000.00; confirmed, so it is owed
        String customer = data.customer().id().toString();
        String order = id(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","requiredDate":"%s",
                 "items":[{"productType":"KORA_WARP","requiredWeightKg":90.000,"ratePerKg":1000.00}]}"""
                .formatted(customer, today(), today().plusDays(10)))).andExpect(status().isCreated()));
        asOwner(postJson("/api/v1/orders/" + order + "/confirm", "")).andExpect(status().isOk());
        String item = body(asOwner(get("/api/v1/orders/" + order))).get("items").get(0).get("id").asString();

        // 3. plan a 100 kg batch for the item and start it: raw silk -> INTERNAL_WIP, the order is locked
        String batch = id(asOwner(postJson("/api/v1/production-batches", """
                {"productType":"KORA_WARP","productionDate":"%s","inputWeightKg":100.000,
                 "orderItems":[{"orderItemId":"%s","allocatedWeightKg":90.000}]}""".formatted(today(), item)))
                .andExpect(status().isCreated()));
        asOwner(postJson("/api/v1/production-batches/" + batch + "/start", "{}")).andExpect(status().isOk());
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.productionBatches[0].id").value(batch));
        assertThat(kg("RAW_SILK", "RAW_STOCK")).isEqualByComparingTo("0.000");
        assertThat(kg("KORA_WARP", "INTERNAL_WIP")).isEqualByComparingTo("100.000");

        // 4. a worker warps the batch: 100 kg at 5.00 = 500.00
        String worker = data.worker().id().toString();
        asOwner(postJson("/api/v1/work-records", """
                {"workerId":"%s","workDate":"%s","workType":"WARPING","quantityKg":100.000,"rate":5.00,
                 "productionBatchId":"%s"}""".formatted(worker, today(), batch))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(500.00));

        // the dashboard mid-way: one batch running, the order in progress, 100 kg in WIP, the worker owed 500.00
        asOwner(get("/api/v1/dashboard/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.productionBatches.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.ordersByStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.stock.internalWipKg").value(100.000))
                .andExpect(jsonPath("$.workerPayable").value(500.00))
                .andExpect(jsonPath("$.customerReceivables").value(90000.00))
                .andExpect(jsonPath("$.supplierPayables").value(80000.00));

        // 5. complete: 94 warp, 4 wastage, 2 discrepancy (100 in = 100 accounted)
        asOwner(postJson("/api/v1/production-batches/" + batch + "/complete", """
                {"outputWeightKg":94.000,"wastageWeightKg":4.000,"discrepancyWeightKg":2.000}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.wastagePercent").value(4.00))
                .andExpect(jsonPath("$.workRecords.length()").value(1));
        assertThat(kg("KORA_WARP", "INTERNAL_WIP")).isEqualByComparingTo("0.000");
        assertThat(kg("KORA_WARP", "FINISHED_STOCK")).isEqualByComparingTo("94.000");

        // 6. ready and deliver 90 kg from finished stock
        asOwner(postJson("/api/v1/orders/" + order + "/ready", "")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/orders/" + order + "/deliver", """
                {"deliveryDate":"%s"}""".formatted(today()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        assertThat(kg("KORA_WARP", "FINISHED_STOCK")).isEqualByComparingTo("4.000");

        // 7. settle: customer pays 90000 into the bank, the supplier gets 80000, the worker 500 in cash
        pay("CUSTOMER", customer, "90000.00", "BANK_TRANSFER", bank).andExpect(status().isCreated());
        pay("SUPPLIER", supplier, "80000.00", "BANK_TRANSFER", bank).andExpect(status().isCreated());
        pay("WORKER", worker, "500.00", "CASH", cash).andExpect(status().isCreated());
        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(jsonPath("$.totalOutstanding").value(0)).andExpect(jsonPath("$.advance").value(0));
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        asOwner(get("/api/v1/suppliers/" + supplier + "/payables")).andExpect(jsonPath("$.totalPayable").value(0));
        asOwner(get("/api/v1/workers/" + worker + "/payable")).andExpect(jsonPath("$.pending").value(0));

        // 8. money and stock read back: bank 200000 + 90000 - 80000, cash 5000 - 500; wastage 6 kg (2 discrepancy)
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(210000.00));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(4500.00));
        asOwner(get("/api/v1/inventory/summary")).andExpect(jsonPath("$.internalWipKg").value(0))
                .andExpect(jsonPath("$.wastageToDateKg[?(@.productType == 'KORA_WARP')].quantityKg").value(6.0));
        asOwner(get("/api/v1/dashboard/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(4500.00))
                .andExpect(jsonPath("$.totalAvailable").value(214500.00))
                .andExpect(jsonPath("$.customerReceivables").value(0.0))
                .andExpect(jsonPath("$.supplierPayables").value(0.0))
                .andExpect(jsonPath("$.workerPayable").value(0.0))
                .andExpect(jsonPath("$.stock.finishedKoraWarpKg").value(4.000))
                .andExpect(jsonPath("$.stock.internalWipKg").value(0.0))
                .andExpect(jsonPath("$.productionBatches.IN_PROGRESS").value(0))
                .andExpect(jsonPath("$.ordersByStatus.IN_PROGRESS").value(0))
                .andExpect(jsonPath("$.recentTransactions.length()").value(5));
        // receipt, consumption, output, wastage, discrepancy, delivery
        asOwner(get("/api/v1/inventory/movements")).andExpect(jsonPath("$.totalElements").value(6));
        assertProjectionMatchesLedger();
    }

    private ResultActions pay(String purpose, String party, String amount, String method, String account)
            throws Exception {
        return asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"%s","partyId":"%s","amount":%s,"paymentMethod":"%s","financialAccountId":"%s"}"""
                .formatted(purpose, party, amount, method, account)));
    }

    private String id(ResultActions result) throws Exception {
        return body(result).get("id").asString();
    }

    private BigDecimal kg(String product, String location) {
        return jdbc.queryForObject("select coalesce(sum(quantity_kg), 0) from inventory_balances where product_type = ?"
                + " and location = ?", BigDecimal.class, product, location);
    }

    private void assertProjectionMatchesLedger() {
        assertThat(jdbc.queryForObject("select count(*) from inventory_balances b where b.quantity_kg <> coalesce(("
                + " select sum(case when m.to_location = b.location and m.to_product_type = b.product_type then"
                + " m.quantity_kg else 0 end) - sum(case when m.from_location = b.location and m.product_type ="
                + " b.product_type then m.quantity_kg else 0 end) from material_movements m), 0)", Integer.class))
                .isZero();
    }
}

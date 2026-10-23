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

// The job-work loop end to end through the API (NFR-TEST-06): buy raw silk, send it to a manufacturer, take the silk
// warp back in two receipts with losses, deliver to the customer, and settle customer and manufacturer. External WIP,
// payables and the stock projection are read back from the server.
class OutsourcingScenarioTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-TEST-06 issue receive deliver pay manufacturer")
    void issueReceiveDeliverPayManufacturer() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "300000.00");

        // 1. 200 kg raw silk bought and received
        String supplier = data.supplier().id().toString();
        String purchase = id(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":200.000,"ratePerKg":800.00,
                 "paymentType":"CREDIT"}""".formatted(supplier, today()))).andExpect(status().isCreated()));
        asOwner(postWithKey("/api/v1/purchases/" + purchase + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":200.000,"acceptedWeightKg":200.000,"rejectedWeightKg":0.000}"""
                .formatted(today()))).andExpect(status().isCreated());

        // 2. a confirmed order for 190 kg silk warp at 1100.00
        String customer = data.customer().id().toString();
        String order = id(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","requiredDate":"%s",
                 "items":[{"productType":"SILK_WARP","requiredWeightKg":190.000,"ratePerKg":1100.00}]}"""
                .formatted(customer, today(), today().plusDays(20)))).andExpect(status().isCreated()));
        asOwner(postJson("/api/v1/orders/" + order + "/confirm", "")).andExpect(status().isOk());
        String item = body(asOwner(get("/api/v1/orders/" + order))).get("items").get(0).get("id").asString();

        // 3. a job for the item at 120.00 per kg; issuing sends 200 kg out and locks the order
        String manufacturer = data.manufacturer().id().toString();
        String job = id(asOwner(postJson("/api/v1/outsourcing-jobs", """
                {"manufacturerId":"%s","productType":"SILK_WARP","issuedWeightKg":200.000,"ratePerKg":120.00,
                 "orderItems":[{"orderItemId":"%s","allocatedWeightKg":190.000}]}""".formatted(manufacturer, item)))
                .andExpect(status().isCreated()));
        asOwner(postJson("/api/v1/outsourcing-jobs/" + job + "/issue", "{}")).andExpect(status().isOk());
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.outsourcingJobs[0].id").value(job));
        asOwner(get("/api/v1/inventory/external-wip")).andExpect(jsonPath("$.totalOutsideKg").value(200.000));

        // 4. two receipts: 150 + 3 loss, then 45 + 2 loss -> completed, 195 kg warp in finished stock
        receive(job, "150.000", "3.000").andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"));
        asOwner(get("/api/v1/inventory/external-wip")).andExpect(jsonPath("$.totalOutsideKg").value(47.000))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].outsideKg").value(47.000));
        asOwner(get("/api/v1/manufacturers/" + manufacturer + "/payable"))
                .andExpect(jsonPath("$.totalPayable").value(18000.00));
        receive(job, "45.000", "2.000").andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.receipts.length()").value(2));
        asOwner(get("/api/v1/inventory/external-wip")).andExpect(jsonPath("$.totalOutsideKg").value(0))
                .andExpect(jsonPath("$.manufacturers.length()").value(0));
        assertThat(kg("SILK_WARP", "FINISHED_STOCK")).isEqualByComparingTo("195.000");

        // 5. ready and deliver 190 kg
        asOwner(postJson("/api/v1/orders/" + order + "/ready", "")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/orders/" + order + "/deliver", """
                {"deliveryDate":"%s"}""".formatted(today()))).andExpect(status().isOk());
        assertThat(kg("SILK_WARP", "FINISHED_STOCK")).isEqualByComparingTo("5.000");

        // 6. settle: the customer pays 209000; the manufacturer is paid 195 kg x 120.00 = 23400 (historical rate)
        pay("CUSTOMER", customer, "209000.00", bank).andExpect(status().isCreated());
        pay("MANUFACTURER", manufacturer, "23400.00", bank).andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocations[0].targetId").value(job));
        asOwner(get("/api/v1/manufacturers/" + manufacturer + "/payable"))
                .andExpect(jsonPath("$.totalPayable").value(0)).andExpect(jsonPath("$.jobs.length()").value(0));
        asOwner(get("/api/v1/outsourcing-jobs/" + job)).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        asOwner(get("/api/v1/customers/" + customer + "/outstanding")).andExpect(jsonPath("$.totalOutstanding").value(0));
        // the supplier is still owed the whole purchase
        asOwner(get("/api/v1/suppliers/" + supplier + "/payables")).andExpect(jsonPath("$.totalPayable").value(160000.00));

        // 7. money and stock read back: bank 300000 + 209000 - 23400; wastage 5 kg; ledger = projection
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(485600.00));
        asOwner(get("/api/v1/inventory/summary")).andExpect(jsonPath("$.externalWipKg").value(0))
                .andExpect(jsonPath("$.wastageToDateKg[?(@.productType == 'SILK_WARP')].quantityKg").value(5.0));
        // purchase receipt, issue, 2 x (outsource receipt + loss), delivery
        asOwner(get("/api/v1/inventory/movements")).andExpect(jsonPath("$.totalElements").value(7));
        assertThat(jdbc.queryForObject("select count(*) from inventory_balances b where b.quantity_kg <> coalesce(("
                + " select sum(case when m.to_location = b.location and m.to_product_type = b.product_type then"
                + " m.quantity_kg else 0 end) - sum(case when m.from_location = b.location and m.product_type ="
                + " b.product_type then m.quantity_kg else 0 end) from material_movements m), 0)", Integer.class))
                .isZero();
    }

    private ResultActions receive(String job, String received, String wastage) throws Exception {
        return asOwner(postWithKey("/api/v1/outsourcing-jobs/" + job + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":%s,"wastageWeightKg":%s}""".formatted(today(), received,
                wastage))).andExpect(status().isOk());
    }

    private ResultActions pay(String purpose, String party, String amount, String account) throws Exception {
        return asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"%s","partyId":"%s","amount":%s,"paymentMethod":"BANK_TRANSFER","financialAccountId":"%s"}"""
                .formatted(purpose, party, amount, account)));
    }

    private String id(ResultActions result) throws Exception {
        return body(result).get("id").asString();
    }

    private BigDecimal kg(String product, String location) {
        return jdbc.queryForObject("select coalesce(sum(quantity_kg), 0) from inventory_balances where product_type = ?"
                + " and location = ?", BigDecimal.class, product, location);
    }
}

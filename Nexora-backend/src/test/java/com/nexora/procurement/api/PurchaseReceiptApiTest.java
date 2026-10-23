package com.nexora.procurement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.support.AbstractApiTest;

import tools.jackson.databind.JsonNode;

class PurchaseReceiptApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PROC-19 receive replay with same key creates one receipt")
    void receive_replayWithSameKey_createsOneReceipt() throws Exception {
        String purchase = createPurchase(data.supplier().id(), "RAW_SILK", "100.000", "800.00");
        String body = """
                {"receiptDate":"2026-10-03","receivedWeightKg":40.000,"acceptedWeightKg":40.000,"rejectedWeightKg":0.000}""";

        replayTwice("/api/v1/purchases/" + purchase + "/receipts", body, 201);

        // one receipt, one stock movement: raw stock is 40, not 80
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from material_receipts",
                Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select quantity_kg from inventory_balances"
                + " where product_type = 'RAW_SILK' and location = 'RAW_STOCK'", java.math.BigDecimal.class))
                .isEqualByComparingTo("40.000");
        // the route requires a key
        asOwner(postJson("/api/v1/purchases/" + purchase + "/receipts", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
    }

    @Test
    @DisplayName("FR-PROC-08 FR-PROC-10 receive accepted weight only enters raw stock")
    void receive_acceptedWeightOnly_entersRawStock() throws Exception {
        String purchase = createPurchase(data.supplier().id(), "RAW_SILK", "250.000", "800.00");

        // FR-PROC-10 AC1: 100 kg arrive, 95 accepted, 5 rejected -> stock rises by exactly 95
        JsonNode body = body(receive(purchase, "100.000", "95.000", "5.000")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        startsWith("/api/v1/purchases/" + purchase + "/receipts/")))
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.receipts[0].receiptNumber").value(startsWith("RCT-")))
                .andExpect(jsonPath("$.receipts[0].movementNumber").value(startsWith("MOV-"))));
        assertThat(rawSilk()).isEqualByComparingTo("95.000");
        JsonNode receipt = body.get("receipts").get(0);
        // exactly one RECEIPT movement, pointing at the receipt (FR-INV-01 AC1)
        Map<String, Object> movement = jdbc.queryForMap("select * from material_movements");
        assertThat(movement).containsEntry("movement_type", "RECEIPT").containsEntry("from_location", "SUPPLIER")
                .containsEntry("to_location", "RAW_STOCK").containsEntry("reference_type", "MATERIAL_RECEIPT")
                .containsEntry("reference_id", UUID.fromString(receipt.get("id").asString()))
                .containsEntry("reference_number", receipt.get("receiptNumber").asString());
        assertThat((BigDecimal) movement.get("quantity_kg")).isEqualByComparingTo("95.000");
        // the receipt can be read on its own
        asOwner(get("/api/v1/purchases/" + purchase + "/receipts/" + receipt.get("id").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedWeightKg").value(95.0))
                .andExpect(jsonPath("$.movementNumber").value(movement.get("movement_number")));

        // FR-PROC-10 AC2: nothing accepted -> no movement, stock unchanged, movement number null
        receive(purchase, "40.000", "0.000", "40.000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receipts[1].movementNumber").value(nullValue()));
        assertThat(rawSilk()).isEqualByComparingTo("95.000");
        assertThat(count("material_movements")).isEqualTo(1);

        // FR-PROC-09 AC2: accepted + rejected above received -> 400 on the weight field, nothing stored
        receive(purchase, "10.000", "9.000", "2.000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("acceptedWeightKg"));
        // FR-PROC-08 AC4: received 0
        receive(purchase, "0.000", "0.000", "0.000").andExpect(status().isBadRequest());
        assertThat(count("material_receipts")).isEqualTo(2);

        // FR-PROC-10 AC3: when the movement cannot be written (stock row held by another transaction past the
        // 5 s lock timeout) the receipt and the status change are not stored either
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            Future<?> holding = holder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                jdbc.queryForList("select id from inventory_balances where product_type = 'RAW_SILK' for update");
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            receive(purchase, "10.000", "10.000", "0.000")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
            release.countDown();
            holding.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            holder.shutdownNow();
        }
        assertThat(count("material_receipts")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select sum(received_weight_kg) from material_receipts", BigDecimal.class))
                .isEqualByComparingTo("140.000");
        assertThat(rawSilk()).isEqualByComparingTo("95.000");
    }

    @Test
    @DisplayName("FR-PROC-08 NFR-TEST-04 receive beyond ordered returns409 purchase receipt exceeded")
    void receive_beyondOrdered_returns409PurchaseReceiptExceeded() throws Exception {
        String purchase = createPurchase(data.supplier().id(), "RAW_SILK", "250.000", "800.00");
        receive(purchase, "180.000", "180.000", "0.000").andExpect(status().isCreated());

        // AC2: 180 of 250 received, 100 more -> 409 and nothing changes
        receive(purchase, "100.000", "100.000", "0.000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_RECEIPT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("70.000")));
        assertThat(count("material_receipts")).isEqualTo(1);
        assertThat(count("material_movements")).isEqualTo(1);
        assertThat(rawSilk()).isEqualByComparingTo("180.000");
        asOwner(get("/api/v1/purchases/" + purchase)).andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"));
        // an unknown purchase is a 404
        receive(UUID.randomUUID().toString(), "1.000", "1.000", "0.000").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-PROC-17 receive vuda warp enters raw stock as vuda")
    void receive_vudaWarp_entersRawStockAsVuda() throws Exception {
        String purchase = createPurchase(data.supplier().id(), "VUDA_WARP", "50.000", "300.00");

        receive(purchase, "50.000", "50.000", "0.000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FULLY_RECEIVED"));

        // AC1: vuda warp is trading stock in RAW_STOCK, not raw silk
        assertThat(body(asOwner(get("/api/v1/inventory/summary"))).get("vudaWarpKg").decimalValue())
                .isEqualByComparingTo("50.000");
        assertThat(jdbc.queryForObject("select product_type from material_movements", String.class))
                .isEqualTo("VUDA_WARP");
        assertThat(jdbc.queryForObject("select count(*) from inventory_balances where product_type = 'RAW_SILK'"
                + " and quantity_kg > 0", Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-PROC-11 receive 250 ordered100 received then plus80 accepted then plus100 rejected")
    void receive_250Ordered100Received_thenPlus80Accepted_thenPlus100Rejected() throws Exception {
        String purchase = createPurchase(data.supplier().id(), "RAW_SILK", "250.000", "800.00");

        // the expected-behaviour table: 250 ordered
        receive(purchase, "100.000", "100.000", "0.000")                       // 100 received -> 150 remaining
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.receivedWeightKg").value(100.0))
                .andExpect(jsonPath("$.remainingWeightKg").value(150.0));
        receive(purchase, "80.000", "80.000", "0.000")                         // +80 -> 180 received, 70 remaining
                .andExpect(jsonPath("$.receivedWeightKg").value(180.0))
                .andExpect(jsonPath("$.remainingWeightKg").value(70.0))
                .andExpect(jsonPath("$.payableAmount").value(144000.0));
        receive(purchase, "100.000", "100.000", "0.000")                       // +100 -> rejected
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_RECEIPT_EXCEEDED"));
        receive(purchase, "70.000", "70.000", "0.000")                         // exactly the rest
                .andExpect(jsonPath("$.status").value("FULLY_RECEIVED"))
                .andExpect(jsonPath("$.remainingWeightKg").value(0.0));
        // FR-PROC-11 AC3: a fully received purchase takes no receipt of any weight
        receive(purchase, "0.001", "0.001", "0.000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_RECEIPT_EXCEEDED"));
        assertThat(rawSilk()).isEqualByComparingTo("250.000");
        assertThat(count("material_receipts")).isEqualTo(3);
    }

    private BigDecimal rawSilk() {
        return jdbc.queryForObject("select coalesce(sum(quantity_kg), 0) from inventory_balances"
                + " where product_type = 'RAW_SILK' and location = 'RAW_STOCK'", BigDecimal.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}

package com.nexora.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.application.MovementCommand;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.orders.infrastructure.CustomerOrderRepository;
import com.nexora.orders.infrastructure.CustomerReturnRepository;
import com.nexora.support.AbstractApiTest;

import tools.jackson.databind.JsonNode;

class CustomerReturnApiTest extends AbstractApiTest {

    @Autowired
    CustomerOrderRepository orderRepository;

    @Autowired
    CustomerReturnRepository returnRepository;

    @Autowired
    InventoryService inventoryService;

    @Test
    @DisplayName("FR-ORD-20 FR-INV-17 record return of usable warp increases finished stock and credits amount")
    void recordReturn_usableWarp_increasesFinishedStockAndCreditsAmount() throws Exception {
        UUID customer = data.customer().id();
        seedStock(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK, new BigDecimal("50.000"));

        JsonNode order = createAndDeliverOrder(customer, ProductType.SILK_WARP, "20.000", "5000.00");
        String orderId = order.get("id").asText();
        String itemId = order.get("items").get(0).get("id").asText();

        // Stock in FINISHED_STOCK should now be 30.000 (50 - 20)
        assertStock(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK, "30.000");

        String returnPayload = """
                {"orderItemId":"%s","returnDate":"%s","returnedWeightKg":5.000,"usable":true,
                 "reason":"Reed mark on border","notes":"Credit note"}"""
                .formatted(itemId, today());

        JsonNode ret = body(asOwner(postWithKey("/api/v1/orders/" + orderId + "/returns", returnPayload))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/orders/" + orderId + "/returns/")))
                .andExpect(jsonPath("$.returnNumber").value(Matchers.startsWith("CRN-")))
                .andExpect(jsonPath("$.returnedWeightKg").value(5.0))
                .andExpect(jsonPath("$.ratePerKg").value(5000.00))
                .andExpect(jsonPath("$.creditedAmount").value(25000.00))
                .andExpect(jsonPath("$.usable").value(true))
                .andExpect(jsonPath("$.movementNumber").value(Matchers.startsWith("MOV-")))
                .andExpect(jsonPath("$.reason").value("Reed mark on border")));

        // Stock in FINISHED_STOCK is now 35.000 (30 + 5)
        assertStock(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK, "35.000");

        // Verify GET /api/v1/orders/{id}/returns
        asOwner(get("/api/v1/orders/" + orderId + "/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].returnNumber").value(ret.get("returnNumber").asText()))
                .andExpect(jsonPath("$[0].movementNumber").value(ret.get("movementNumber").asText()));
    }

    @Test
    @DisplayName("FR-ORD-20 FR-INV-17 record return of unusable warp routes directly to wastage")
    void recordReturn_unusableWarp_routesToWastage() throws Exception {
        UUID customer = data.customer().id();
        seedStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, new BigDecimal("30.000"));

        JsonNode order = createAndDeliverOrder(customer, ProductType.KORA_WARP, "15.000", "1200.00");
        String orderId = order.get("id").asText();
        String itemId = order.get("items").get(0).get("id").asText();

        assertStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "15.000");

        String returnPayload = """
                {"orderItemId":"%s","returnDate":"%s","returnedWeightKg":3.000,"usable":false,
                 "reason":"Severe water damage during transport"}"""
                .formatted(itemId, today());

        asOwner(postWithKey("/api/v1/orders/" + orderId + "/returns", returnPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usable").value(false))
                .andExpect(jsonPath("$.returnedWeightKg").value(3.0))
                .andExpect(jsonPath("$.creditedAmount").value(3600.00));

        // Finished stock remains 15.000 (unusable material does NOT enter inventory)
        assertStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "15.000");
    }

    @Test
    @DisplayName("FR-ORD-20 return exceeding delivered weight is rejected with 409 INSUFFICIENT_INVENTORY")
    void recordReturn_exceedingDeliveredWeight_returns409() throws Exception {
        UUID customer = data.customer().id();
        seedStock(ProductType.RAW_SILK, StockLocation.RAW_STOCK, new BigDecimal("20.000"));

        JsonNode order = createAndDeliverOrder(customer, ProductType.RAW_SILK, "10.000", "3500.00");
        String orderId = order.get("id").asText();
        String itemId = order.get("items").get(0).get("id").asText();

        // First return: 8.000 kg succeeds
        asOwner(postWithKey("/api/v1/orders/" + orderId + "/returns", """
                {"orderItemId":"%s","returnDate":"%s","returnedWeightKg":8.000,"usable":true,"reason":"Excess"}"""
                .formatted(itemId, today()))).andExpect(status().isCreated());

        // Second return: 3.000 kg exceeds remaining 2.000 kg -> 409 INSUFFICIENT_INVENTORY
        asOwner(postWithKey("/api/v1/orders/" + orderId + "/returns", """
                {"orderItemId":"%s","returnDate":"%s","returnedWeightKg":3.000,"usable":true,"reason":"More excess"}"""
                .formatted(itemId, today())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("only 2.000 kg still returnable")));
    }

    @Test
    @DisplayName("FR-ORD-20 return against undelivered order is rejected with 409 INVALID_STATE_TRANSITION")
    void recordReturn_undeliveredOrder_returns409() throws Exception {
        UUID customer = data.customer().id();
        JsonNode placed = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","requiredDate":"%s",
                 "items":[{"productType":"SILK_WARP","requiredWeightKg":10.000,"ratePerKg":5000.00}]}"""
                .formatted(customer, today(), today().plusDays(7))))
                .andExpect(status().isCreated()));

        String orderId = placed.get("id").asText();
        String itemId = placed.get("items").get(0).get("id").asText();

        asOwner(postWithKey("/api/v1/orders/" + orderId + "/returns", """
                {"orderItemId":"%s","returnDate":"%s","returnedWeightKg":2.000,"usable":true,"reason":"Premature"}"""
                .formatted(itemId, today())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("FR-ORD-20 idempotent replay returns identical response without duplicating rows")
    void recordReturn_idempotentReplay_returnsCachedResponse() throws Exception {
        UUID customer = data.customer().id();
        seedStock(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK, new BigDecimal("20.000"));

        JsonNode order = createAndDeliverOrder(customer, ProductType.SILK_WARP, "10.000", "5000.00");
        String orderId = order.get("id").asText();
        String itemId = order.get("items").get(0).get("id").asText();

        String key = UUID.randomUUID().toString();
        String payload = """
                {"orderItemId":"%s","returnDate":"%s","returnedWeightKg":4.000,"usable":true,"reason":"Replay test"}"""
                .formatted(itemId, today());

        JsonNode first = body(asOwner(postWithKey("/api/v1/orders/" + orderId + "/returns", payload, key))
                .andExpect(status().isCreated()));

        // Replay with exact same key
        JsonNode second = body(asOwner(postWithKey("/api/v1/orders/" + orderId + "/returns", payload, key))
                .andExpect(status().isCreated()));

        assertThat(first.get("id").asText()).isEqualTo(second.get("id").asText());
        assertThat(first.get("returnNumber").asText()).isEqualTo(second.get("returnNumber").asText());
        assertThat(first.get("movementNumber").asText()).isEqualTo(second.get("movementNumber").asText());

        // Verify only 1 return row was created
        assertThat(returnRepository.findByOrderIdOrderByCreatedAtAsc(UUID.fromString(orderId))).hasSize(1);
    }

    @Test
    @DisplayName("FR-ORD-20 return directly reduces customer outstanding obligation and appears on statement")
    void recordReturn_reducesCustomerOutstandingDirectly() throws Exception {
        UUID customer = data.customer().id();
        seedStock(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK, new BigDecimal("20.000"));

        JsonNode order = createAndDeliverOrder(customer, ProductType.SILK_WARP, "10.000", "5000.00");
        String orderId = order.get("id").asText();
        String itemId = order.get("items").get(0).get("id").asText();

        // 1. Prior to return: customer owes 50,000.00
        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(50000.00))
                .andExpect(jsonPath("$.orders[0].amount").value(50000.00))
                .andExpect(jsonPath("$.orders[0].outstanding").value(50000.00));

        // 2. Return 2.000 kg @ 5000.00 = 10,000.00 credit
        asOwner(postWithKey("/api/v1/orders/" + orderId + "/returns", """
                {"orderItemId":"%s","returnDate":"%s","returnedWeightKg":2.000,"usable":true,
                 "reason":"Client downsized order requirement"}"""
                .formatted(itemId, today())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditedAmount").value(10000.00));

        // 3. Outstanding must be exactly 40,000.00
        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(40000.00))
                .andExpect(jsonPath("$.orders[0].amount").value(40000.00))
                .andExpect(jsonPath("$.orders[0].outstanding").value(40000.00));

        // 4. Customer statement reflects the RETURN credit
        asOwner(get("/api/v1/customers/" + customer + "/statement?from=" + today().minusDays(1) + "&to=" + today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].kind").value("ORDER"))
                .andExpect(jsonPath("$.lines[0].debit").value(50000.00))
                .andExpect(jsonPath("$.lines[1].kind").value("RETURN"))
                .andExpect(jsonPath("$.lines[1].credit").value(10000.00))
                .andExpect(jsonPath("$.closingBalance").value(40000.00));

        // 5. GET /api/v1/orders/{orderId}/returns lists the return
        asOwner(get("/api/v1/orders/" + orderId + "/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].returnedWeightKg").value(2.0))
                .andExpect(jsonPath("$[0].creditedAmount").value(10000.00))
                .andExpect(jsonPath("$[0].reason").value("Client downsized order requirement"));
    }

    private JsonNode createAndDeliverOrder(UUID customer, ProductType product, String weight, String rate) throws Exception {
        JsonNode placed = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","requiredDate":"%s",
                 "items":[{"productType":"%s","requiredWeightKg":%s,"ratePerKg":%s}]}"""
                .formatted(customer, today(), today().plusDays(7), product, weight, rate)))
                .andExpect(status().isCreated()));
        String orderId = placed.get("id").asText();

        // Confirm -> Ready -> Deliver
        asOwner(postJson("/api/v1/orders/" + orderId + "/confirm", "")).andExpect(status().isOk());
        asOwner(postJson("/api/v1/orders/" + orderId + "/ready", "")).andExpect(status().isOk());
        return body(asOwner(postWithKey("/api/v1/orders/" + orderId + "/deliver", """
                {"deliveryDate":"%s"}""".formatted(today())))
                .andExpect(status().isOk()));
    }

    private void seedStock(ProductType product, StockLocation location, BigDecimal quantity) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            inventoryService.recordMovement(new MovementCommand(
                    product, null, MovementType.OPENING_BALANCE, null, location,
                    quantity, today(), null, null, null, null, "seed"
            ));
            return null;
        });
    }

    private void assertStock(ProductType product, StockLocation location, String expectedKg) throws Exception {
        JsonNode summary = body(asOwner(get("/api/v1/inventory/summary")).andExpect(status().isOk()));
        boolean found = false;
        for (JsonNode b : summary.get("balances")) {
            if (b.get("productType").asText().equals(product.name())
                    && b.get("location").asText().equals(location.name())) {
                assertThat(b.get("quantityKg").asDouble()).isEqualTo(Double.parseDouble(expectedKg));
                found = true;
                break;
            }
        }
        assertThat(found).as("Balance entry found for " + product + " at " + location).isTrue();
    }
}

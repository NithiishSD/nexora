package com.nexora.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.support.AbstractApiTest;

import tools.jackson.databind.JsonNode;

// E-ORD-08: delivering a READY order moves every item out of stock in one transaction.
class OrderDeliveryApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-07 deliver replay with same key delivers once")
    void deliver_replayWithSameKey_deliversOnce() throws Exception {
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "100.000");
        String id = readyOrder("""
                [{"productType":"KORA_WARP","requiredWeightKg":60.000,"ratePerKg":850.00}]""");

        // a retry after a lost answer gets the stored 200 instead of a 409 for an already delivered order
        replayTwice("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"" + today() + "\"}", 200);

        assertThat(movements("DELIVERY")).hasSize(1);
        assertThat(balance(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("40.000");
    }

    @Test
    @DisplayName("FR-ORD-11 deliver warp items create delivery movements from finished stock")
    void deliver_warpItems_createDeliveryMovementsFromFinishedStock() throws Exception {
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "100.000");
        String id = readyOrder("""
                [{"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00}]""");
        String itemId = body(asOwner(get("/api/v1/orders/" + id))).get("items").get(0).get("id").asString();

        // AC1: finished stock 100 -> 0, one DELIVERY movement of 100 that points at the item and shows the order number
        asOwner(postWithKey("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"" + today() + "\",\"notes\":\"by van\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredOn").value(today().toString()))
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.late").value(false));
        assertThat(balance(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("0.000");
        List<Map<String, Object>> rows = movements("DELIVERY");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("from_location", "FINISHED_STOCK")
                .containsEntry("to_location", "CUSTOMER")
                .containsEntry("reference_type", "ORDER_ITEM")
                .containsEntry("reference_id", UUID.fromString(itemId))
                .containsEntry("reference_number", orderNumber(id))
                .containsEntry("notes", "by van");
        assertThat((BigDecimal) rows.getFirst().get("quantity_kg")).isEqualByComparingTo("100.000");

        // delivering twice is an illegal transition; a missing date is 400; an unknown order is 404
        asOwner(postWithKey("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"" + today() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        asOwner(postWithKey("/api/v1/orders/" + id + "/deliver", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("deliveryDate"));
        asOwner(postWithKey("/api/v1/orders/" + UUID.randomUUID() + "/deliver", "{\"deliveryDate\":\"" + today() + "\"}"))
                .andExpect(status().isNotFound());

        // AC3: a CONFIRMED order that was not marked ready cannot be delivered, and nothing moves
        data.openingStock(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK, "10.000");
        String confirmed = createOrder(data.customer().id(), "SILK_WARP", "10.000", "900.00");
        asOwner(postJson("/api/v1/orders/" + confirmed + "/confirm", "")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/orders/" + confirmed + "/deliver", "{\"deliveryDate\":\"" + today() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(balance(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("FR-ORD-11 FR-ORD-15 deliver raw silk and vuda create direct sale from raw stock")
    void deliver_rawSilkAndVuda_createDirectSaleFromRawStock() throws Exception {
        data.rawSilkInStock("50.000");
        data.openingStock(ProductType.VUDA_WARP, StockLocation.RAW_STOCK, "10.000");
        String id = readyOrder("""
                [{"productType":"RAW_SILK","requiredWeightKg":20.000,"ratePerKg":900.00},
                 {"productType":"VUDA_WARP","requiredWeightKg":4.500,"ratePerKg":1200.00}]""");

        // FR-ORD-15 AC1: a DIRECT_SALE per item from RAW_STOCK; raw silk 50 -> 30, vuda 10 -> 5.5; no sale entity
        asOwner(postWithKey("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"" + today() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        assertThat(balance(ProductType.RAW_SILK, StockLocation.RAW_STOCK)).isEqualByComparingTo("30.000");
        assertThat(balance(ProductType.VUDA_WARP, StockLocation.RAW_STOCK)).isEqualByComparingTo("5.500");
        assertThat(movements("DIRECT_SALE")).extracting(r -> r.get("product_type"), r -> r.get("from_location"))
                .containsExactlyInAnyOrder(tuple("RAW_SILK", "RAW_STOCK"),
                        tuple("VUDA_WARP", "RAW_STOCK"));
        assertThat(movements("DELIVERY")).isEmpty();

        // FR-ORD-15 AC2: 20 kg ordered, 10 kg left after another sale -> 409, stock unchanged
        String second = readyOrder("""
                [{"productType":"RAW_SILK","requiredWeightKg":40.000,"ratePerKg":900.00}]""");
        asOwner(postWithKey("/api/v1/orders/" + second + "/deliver", "{\"deliveryDate\":\"" + today() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        assertThat(balance(ProductType.RAW_SILK, StockLocation.RAW_STOCK)).isEqualByComparingTo("30.000");
    }

    @Test
    @DisplayName("FR-ORD-12 deliver all items together no partial item")
    void deliver_allItemsTogether_noPartialItem() throws Exception {
        // stock covers the first item only
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "100.000");
        data.openingStock(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK, "20.000");
        String id = readyOrder("""
                [{"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00},
                 {"productType":"SILK_WARP","requiredWeightKg":50.000,"ratePerKg":900.00}]""");

        // AC1: the whole delivery is refused and no item moves, not even the one that had stock
        asOwner(postWithKey("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"" + today() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        assertThat(balance(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("100.000");
        assertThat(balance(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("20.000");
        assertThat(movements("DELIVERY")).isEmpty();
        asOwner(get("/api/v1/orders/" + id))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.items[0].status").value("READY"))
                .andExpect(jsonPath("$.items[1].status").value("READY"));
    }

    @Test
    @DisplayName("FR-ORD-11 NFR-REL-02 deliver insufficient stock returns409 and nothing changes")
    void deliver_insufficientStock_returns409AndNothingChanges() throws Exception {
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "80.000");
        String id = readyOrder("""
                [{"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00}]""");
        int movementsBefore = jdbc.queryForObject("select count(*) from material_movements", Integer.class);

        // AC2: 409, the order stays READY without a delivery date, stock stays 80, no movement is stored
        asOwner(postWithKey("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"" + today() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        asOwner(get("/api/v1/orders/" + id))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.deliveredOn").doesNotExist());
        assertThat(balance(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("80.000");
        assertThat(jdbc.queryForObject("select count(*) from material_movements", Integer.class)).isEqualTo(movementsBefore);
    }

    @Test
    @DisplayName("FR-ORD-19 partial item delivery succeeds and deducts stock")
    void partialItemDelivery_succeedsAndDeductsStock() throws Exception {
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "100.000");
        String id = readyOrder("""
                [{"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00}]""");
        String itemId = body(asOwner(get("/api/v1/orders/" + id))).get("items").get(0).get("id").asString();

        // 1. Partial delivery of 40 kg
        asOwner(postWithKey("/api/v1/orders/" + id + "/items/" + itemId + "/deliveries",
                "{\"deliveryDate\":\"" + today() + "\",\"quantityKg\":40.000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("READY"))
                .andExpect(jsonPath("$.status").value("READY"));

        assertThat(balance(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("60.000");
        assertThat(movements("DELIVERY")).hasSize(1);
        assertThat((BigDecimal) movements("DELIVERY").getFirst().get("quantity_kg")).isEqualByComparingTo("40.000");

        // 2. Deliver remaining 60 kg -> item and order become DELIVERED
        asOwner(postWithKey("/api/v1/orders/" + id + "/items/" + itemId + "/deliveries",
                "{\"deliveryDate\":\"" + today() + "\",\"quantityKg\":60.000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredOn").value(today().toString()));

        assertThat(balance(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("0.000");
        assertThat(movements("DELIVERY")).hasSize(2);
    }

    @Test
    @DisplayName("FR-ORD-19 partial item delivery exceeding remaining returns 409")
    void partialItemDelivery_exceedingRemaining_returns409() throws Exception {
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "200.000");
        String id = readyOrder("""
                [{"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00}]""");
        String itemId = body(asOwner(get("/api/v1/orders/" + id))).get("items").get(0).get("id").asString();

        asOwner(postWithKey("/api/v1/orders/" + id + "/items/" + itemId + "/deliveries",
                "{\"deliveryDate\":\"" + today() + "\",\"quantityKg\":120.000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        assertThat(balance(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("200.000");
    }

    @Test
    @DisplayName("FR-ORD-19 partial item delivery insufficient stock returns 409")
    void partialItemDelivery_insufficientStock_returns409() throws Exception {
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "20.000");
        String id = readyOrder("""
                [{"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00}]""");
        String itemId = body(asOwner(get("/api/v1/orders/" + id))).get("items").get(0).get("id").asString();

        asOwner(postWithKey("/api/v1/orders/" + id + "/items/" + itemId + "/deliveries",
                "{\"deliveryDate\":\"" + today() + "\",\"quantityKg\":40.000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));

        assertThat(balance(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("20.000");
    }

    // Creates an order through the API, confirms it and marks it ready (filled from stock).
    private String readyOrder(String items) throws Exception {
        String id = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","items":%s}""".formatted(data.customer().id(), today(), items)))
                .andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());
        asOwner(postJson("/api/v1/orders/" + id + "/ready", "")).andExpect(status().isOk());
        return id;
    }

    private String orderNumber(String id) throws Exception {
        JsonNode order = body(asOwner(get("/api/v1/orders/" + id)));
        return order.get("orderNumber").asString();
    }

    private List<Map<String, Object>> movements(String type) {
        return jdbc.queryForList("select product_type, from_location, to_location, quantity_kg, reference_type,"
                + " reference_id, reference_number, notes from material_movements where movement_type = ?", type);
    }

    private BigDecimal balance(ProductType product, StockLocation location) {
        return jdbc.queryForObject("select quantity_kg from inventory_balances where product_type = ? and location = ?",
                BigDecimal.class, product.name(), location.name());
    }
}

package com.nexora.production.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class ProductionBatchApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PROD-01 FR-PROD-07 create with order links returns planned batch")
    void create_withOrderLinks_returnsPlannedBatch() throws Exception {
        UUID customer = data.customer().id();
        String order = confirmedOrder(customer, "KORA_WARP", "100.000", "850.00");
        String other = confirmedOrder(customer, "KORA_WARP", "40.000", "850.00");
        String item = itemOf(order);

        // AC1: 201, PLANNED, BAT number, no stock change; FR-PROD-07: one batch for items of two orders
        String id = body(plan("KORA_WARP", "100.000", link(item, "60.000") + "," + link(itemOf(other), "40.000"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/production-batches/")))
                .andExpect(jsonPath("$.batchNumber").value("BAT-" + Year.now(clock) + "-0001"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.orderItems.length()").value(2))
                .andExpect(jsonPath("$.orderItems[0].orderNumber").value(Matchers.startsWith("ORD-")))
                .andExpect(jsonPath("$.orderItems[0].lineNo").value(1))
                .andExpect(jsonPath("$.consumptionMovementNumber").value(Matchers.nullValue()))).get("id").asString();
        assertThat(jdbc.queryForObject("select count(*) from material_movements", Integer.class)).isZero();
        // FR-PROD-07 AC1: a second batch for the same item; the order lists both
        plan("KORA_WARP", "20.000", link(item, "20.000")).andExpect(status().isCreated());
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.productionBatches.length()").value(2))
                .andExpect(jsonPath("$.productionBatches[0].id").value(id))
                .andExpect(jsonPath("$.productionBatches[0].allocatedWeightKg").value(60.000));

        // AC3: input 0; FR-PROD-07 AC3: link weight 0; the same item twice -> 400
        plan("KORA_WARP", "0.000", null).andExpect(status().isBadRequest());
        plan("KORA_WARP", "1.000", link(item, "0.000")).andExpect(status().isBadRequest());
        plan("KORA_WARP", "1.000", link(item, "1.000") + "," + link(item, "1.000")).andExpect(status().isBadRequest());
        // AC5: unknown item 404; AC4: a cancelled or delivered order 409; a raw-silk item for a kora batch 422
        plan("KORA_WARP", "1.000", link(UUID.randomUUID().toString(), "1.000")).andExpect(status().isNotFound());
        String cancelled = createOrder(customer, "KORA_WARP", "5.000", "850.00");
        asOwner(postJson("/api/v1/orders/" + cancelled + "/cancel", "{\"reason\":\"x\"}")).andExpect(status().isOk());
        plan("KORA_WARP", "1.000", link(itemOf(cancelled), "1.000")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        String placed = createOrder(customer, "KORA_WARP", "5.000", "850.00");
        plan("KORA_WARP", "1.000", link(itemOf(placed), "1.000")).andExpect(status().isConflict());
        String silk = confirmedOrder(customer, "SILK_WARP", "5.000", "900.00");
        plan("KORA_WARP", "1.000", link(itemOf(silk), "1.000")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_FOR_OPERATION"));
        assertThat(jdbc.queryForObject("select count(*) from production_batches", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-INV-10 create vuda warp returns422 invalid product for operation")
    void create_vudaWarp_returns422InvalidProductForOperation() throws Exception {
        plan("VUDA_WARP", "10.000", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_FOR_OPERATION"));
        plan("RAW_SILK", "10.000", null).andExpect(status().isUnprocessableContent());
        assertThat(jdbc.queryForObject("select count(*) from production_batches", Integer.class)).isZero();
        // the number is not used up by the refused requests
        plan("SILK_WARP", "10.000", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchNumber").value(Matchers.endsWith("-0001")));
    }

    @Test
    @DisplayName("FR-PROD-06 get returns links weights and work records")
    void get_returnsLinksWeightsAndWorkRecords() throws Exception {
        String item = itemOf(confirmedOrder(data.customer().id(), "SILK_WARP", "30.000", "900.00"));
        String id = body(plan("SILK_WARP", "30.000", link(item, "30.000")).andExpect(status().isCreated()))
                .get("id").asString();
        plan("KORA_WARP", "10.000", null).andExpect(status().isCreated());

        asOwner(get("/api/v1/production-batches/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.inputWeightKg").value(30.000))
                .andExpect(jsonPath("$.outputWeightKg").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.wastagePercent").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.orderItems[0].orderItemId").value(item))
                .andExpect(jsonPath("$.orderItems[0].allocatedWeightKg").value(30.000));
        // the list and its filters (AC1: status; also product, order, date range)
        asOwner(get("/api/v1/production-batches?status=PLANNED")).andExpect(jsonPath("$.totalElements").value(2));
        asOwner(get("/api/v1/production-batches?status=IN_PROGRESS")).andExpect(jsonPath("$.totalElements").value(0));
        asOwner(get("/api/v1/production-batches?productType=SILK_WARP")).andExpect(jsonPath("$.totalElements").value(1));
        String order = body(asOwner(get("/api/v1/production-batches/" + id))).get("orderItems").get(0).get("orderId")
                .asString();
        asOwner(get("/api/v1/production-batches?orderId=" + order)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        asOwner(get("/api/v1/production-batches?from=" + today().plusDays(1))).andExpect(jsonPath("$.totalElements").value(0));
        asOwner(get("/api/v1/production-batches?sort=notes")).andExpect(status().isBadRequest());
        // AC2: unknown 404; AC3: no PRODUCTION_VIEW -> 403 (STAFF has it, a token without it does not)
        asOwner(get("/api/v1/production-batches/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/production-batches").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(
                        tokens.mint(UUID.randomUUID(), java.util.List.of("ORDER_VIEW"), clock.instant(),
                                java.time.Duration.ofMinutes(5)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/production-batches").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-PROD-11 patch planned batch changes input and links")
    void patch_plannedBatch_changesInputAndLinks() throws Exception {
        UUID customer = data.customer().id();
        String a = itemOf(confirmedOrder(customer, "KORA_WARP", "100.000", "850.00"));
        String b = itemOf(confirmedOrder(customer, "KORA_WARP", "50.000", "850.00"));
        String id = body(plan("KORA_WARP", "100.000", link(a, "60.000")).andExpect(status().isCreated()))
                .get("id").asString();

        // AC1: input 110, links replaced as a whole, notes set; the product type cannot be changed by a PATCH
        asOwner(patch("/api/v1/production-batches/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                {"inputWeightKg":110.000,"productType":"SILK_WARP","notes":"rush",
                 "orderItems":[%s,%s]}""".formatted(link(a, "70.000"), link(b, "40.000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputWeightKg").value(110.000))
                .andExpect(jsonPath("$.productType").value("KORA_WARP"))
                .andExpect(jsonPath("$.notes").value("rush"))
                .andExpect(jsonPath("$.orderItems.length()").value(2))
                .andExpect(jsonPath("$.orderItems[0].allocatedWeightKg").value(70.000));
        // only notes: the links stay
        asOwner(patch("/api/v1/production-batches/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"\"}"))
                .andExpect(jsonPath("$.notes").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.orderItems.length()").value(2));
        // AC2: not PLANNED -> 409 (started directly in the table, without stock)
        jdbc.update("update production_batches set status = 'IN_PROGRESS', started_at = now() where id = ?::uuid", id);
        asOwner(patch("/api/v1/production-batches/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"inputWeightKg\":1.000}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        // FR-PROD-10: no PUT, no DELETE
        asOwner(put("/api/v1/production-batches/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(delete("/api/v1/production-batches/" + id)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("FR-PROD-02 FR-ORD-08 start consumes raw silk and locks orders")
    void start_consumesRawSilkAndLocksOrders() throws Exception {
        data.rawSilkInStock("150.000");
        UUID customer = data.customer().id();
        String order = confirmedOrder(customer, "KORA_WARP", "100.000", "850.00");
        String id = body(plan("KORA_WARP", "100.000", link(itemOf(order), "100.000")).andExpect(status().isCreated()))
                .get("id").asString();

        // AC1: IN_PROGRESS, one CONSUMPTION movement RAW_STOCK -> INTERNAL_WIP (as kora warp)
        asOwner(postJson("/api/v1/production-batches/" + id + "/start", "{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.consumptionMovementNumber").value(Matchers.startsWith("MOV-")));
        assertThat(kg("RAW_SILK", "RAW_STOCK")).isEqualByComparingTo("50.000");
        assertThat(kg("KORA_WARP", "INTERNAL_WIP")).isEqualByComparingTo("100.000");
        assertThat(jdbc.queryForObject("select count(*) from material_movements where movement_type = 'CONSUMPTION'"
                + " and reference_id = ?::uuid", Integer.class, id)).isEqualTo(1);
        // FR-ORD-08: the order and its item are in progress and locked against edit and cancel
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].status").value("IN_PROGRESS"));
        asOwner(postJson("/api/v1/orders/" + order + "/cancel", "{\"reason\":\"x\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_LOCKED"));
        // a second start is refused, nothing moves twice
        asOwner(post("/api/v1/production-batches/" + id + "/start")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(kg("RAW_SILK", "RAW_STOCK")).isEqualByComparingTo("50.000");
        // a batch for an order already in progress starts too; the order stays IN_PROGRESS
        String more = body(plan("KORA_WARP", "10.000", link(itemOf(order), "10.000"))).get("id").asString();
        asOwner(post("/api/v1/production-batches/" + more + "/start")).andExpect(status().isOk());
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("FR-PROD-08 start insufficient raw silk returns409 and no status change")
    void start_insufficientRawSilk_returns409AndNoStatusChange() throws Exception {
        data.rawSilkInStock("50.000");
        String order = confirmedOrder(data.customer().id(), "SILK_WARP", "100.000", "900.00");
        String id = body(plan("SILK_WARP", "100.000", link(itemOf(order), "100.000")).andExpect(status().isCreated()))
                .get("id").asString();

        asOwner(post("/api/v1/production-batches/" + id + "/start")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        // all or nothing: batch PLANNED, order CONFIRMED, stock untouched, no movement
        asOwner(get("/api/v1/production-batches/" + id)).andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.startedAt").value(Matchers.nullValue()));
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
        assertThat(kg("RAW_SILK", "RAW_STOCK")).isEqualByComparingTo("50.000");
        assertThat(jdbc.queryForObject("select count(*) from material_movements where movement_type = 'CONSUMPTION'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-PROD-03 FR-PROD-08 complete 100 in94 out4 waste2 discrepancy succeeds")
    void complete_100In94Out4Waste2Discrepancy_succeeds() throws Exception {
        data.rawSilkInStock("110.000");
        String id = startedBatch("100.000");
        // AC4: a planned batch cannot be completed; AC3: negative output 400
        String planned = body(plan("KORA_WARP", "5.000", null)).get("id").asString();
        complete(planned, "5.000", "0.000", "0.000").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        complete(id, "-1.000", "0.000", "0.000").andExpect(status().isBadRequest());

        // AC1: finished warp +94, wastage 6 (2 of it discrepancy), INTERNAL_WIP -100
        complete(id, "94.000", "4.000", "2.000").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.outputWeightKg").value(94.000))
                .andExpect(jsonPath("$.wastageWeightKg").value(4.000))
                .andExpect(jsonPath("$.discrepancyWeightKg").value(2.000))
                .andExpect(jsonPath("$.wastagePercent").value(4.00));
        assertThat(kg("KORA_WARP", "FINISHED_STOCK")).isEqualByComparingTo("94.000");
        assertThat(kg("KORA_WARP", "INTERNAL_WIP")).isEqualByComparingTo("0.000");
        assertThat(jdbc.queryForList("select reason from material_movements where movement_type = 'WASTAGE'"
                + " and reference_id = ?::uuid order by quantity_kg desc", String.class, id))
                .containsExactly("PRODUCTION_WASTAGE", "PRODUCTION_DISCREPANCY");
        assertThat(jdbc.queryForObject("select sum(quantity_kg) from material_movements where to_location = 'WASTAGE'",
                java.math.BigDecimal.class)).isEqualByComparingTo("6.000");
        // FR-PROD-04 AC1: the discrepancy stays visible on the batch
        asOwner(get("/api/v1/production-batches/" + id)).andExpect(jsonPath("$.discrepancyWeightKg").value(2.000));
        // completed once only; zero wastage and discrepancy move nothing
        complete(id, "94.000", "4.000", "2.000").andExpect(status().isConflict());
        String clean = startedBatch("10.000");
        complete(clean, "10.000", "0.000", "0.000").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from material_movements where reference_id = ?::uuid",
                Integer.class, clean)).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-PROD-03 FR-PROD-04 complete 100 in94 out10 waste returns422 not reconciled")
    void complete_100In94Out10Waste_returns422NotReconciled() throws Exception {
        data.rawSilkInStock("100.000");
        String id = startedBatch("100.000");

        complete(id, "94.000", "10.000", "0.000").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRODUCTION_NOT_RECONCILED"));
        // FR-PROD-04 AC2: 96 accounted, no discrepancy entered
        complete(id, "94.000", "2.000", "0.000").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRODUCTION_NOT_RECONCILED"));
        // nothing changed
        asOwner(get("/api/v1/production-batches/" + id)).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.outputWeightKg").value(Matchers.nullValue()));
        assertThat(kg("KORA_WARP", "INTERNAL_WIP")).isEqualByComparingTo("100.000");
        assertThat(jdbc.queryForObject("select count(*) from material_movements where reference_id = ?::uuid",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-PROD-05 cancel planned sets cancelled")
    void cancel_planned_setsCancelled() throws Exception {
        String id = body(plan("SILK_WARP", "20.000", null)).get("id").asString();

        asOwner(postJson("/api/v1/production-batches/" + id + "/cancel", "{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        asOwner(postJson("/api/v1/production-batches/" + id + "/cancel", "{\"reason\":\"Order changed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'PRODUCTION_CANCELLED'"
                + " and entity_id = ?::uuid and reason = 'Order changed'", Integer.class, id)).isEqualTo(1);
        // a cancelled batch cannot start; AC2: a started batch cannot be cancelled
        asOwner(post("/api/v1/production-batches/" + id + "/start")).andExpect(status().isConflict());
        data.rawSilkInStock("10.000");
        String started = startedBatch("10.000");
        asOwner(postJson("/api/v1/production-batches/" + started + "/cancel", "{\"reason\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(jdbc.queryForObject("select count(*) from material_movements", Integer.class)).isEqualTo(2);
    }

    // A KORA_WARP batch of this input, planned and started (raw silk must be in stock).
    private String startedBatch(String input) throws Exception {
        String id = body(plan("KORA_WARP", input, null).andExpect(status().isCreated())).get("id").asString();
        asOwner(post("/api/v1/production-batches/" + id + "/start")).andExpect(status().isOk());
        return id;
    }

    private ResultActions complete(String id, String output, String wastage, String discrepancy) throws Exception {
        return asOwner(postJson("/api/v1/production-batches/" + id + "/complete", """
                {"outputWeightKg":%s,"wastageWeightKg":%s,"discrepancyWeightKg":%s}""".formatted(output, wastage,
                discrepancy)));
    }

    private java.math.BigDecimal kg(String product, String location) {
        return jdbc.queryForObject("select coalesce(sum(quantity_kg), 0) from inventory_balances where product_type = ?"
                + " and location = ?", java.math.BigDecimal.class, product, location);
    }

    private ResultActions plan(String product, String input, String links) throws Exception {
        return asOwner(postJson("/api/v1/production-batches", """
                {"productType":"%s","productionDate":"%s","inputWeightKg":%s%s}""".formatted(product, today(), input,
                links == null ? "" : ",\"orderItems\":[" + links + "]")));
    }

    private static String link(String itemId, String kg) {
        return "{\"orderItemId\":\"" + itemId + "\",\"allocatedWeightKg\":" + kg + "}";
    }

    private String itemOf(String orderId) throws Exception {
        return body(asOwner(get("/api/v1/orders/" + orderId))).get("items").get(0).get("id").asString();
    }
}

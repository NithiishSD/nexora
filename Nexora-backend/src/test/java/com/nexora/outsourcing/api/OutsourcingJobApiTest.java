package com.nexora.outsourcing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

class OutsourcingJobApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-OUT-01 FR-OUT-08 create without order is accepted")
    void create_withoutOrder_isAccepted() throws Exception {
        UUID manufacturer = data.manufacturer().id();

        // AC1: 201, CREATED, JOB number, historical rate, nothing outside, no stock change
        create(manufacturer, "KORA_WARP", "200.000", "120.00", null).andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/outsourcing-jobs/")))
                .andExpect(jsonPath("$.jobNumber").value("JOB-" + Year.now(clock) + "-0001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.manufacturer.id").value(manufacturer.toString()))
                .andExpect(jsonPath("$.issuedWeightKg").value(200.000))
                .andExpect(jsonPath("$.ratePerKg").value(120.00))
                .andExpect(jsonPath("$.outsideWeightKg").value(0))
                .andExpect(jsonPath("$.payableAmount").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.issueDate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.orderItems.length()").value(0));
        assertThat(jdbc.queryForObject("select count(*) from material_movements", Integer.class)).isZero();

        // with a link to a confirmed kora order
        UUID customer = data.customer().id();
        String item = itemOf(confirmedOrder(customer, "KORA_WARP", "50.000", "850.00"));
        create(manufacturer, "KORA_WARP", "50.000", "120.00", link(item, "50.000")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderItems[0].orderItemId").value(item))
                .andExpect(jsonPath("$.orderItems[0].orderNumber").value(Matchers.startsWith("ORD-")));
        // AC3: vuda 422; AC5: negative rate 400; FR-OUT-08 AC2: a cancelled order's item 409
        create(manufacturer, "VUDA_WARP", "10.000", "120.00", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_FOR_OPERATION"));
        create(manufacturer, "KORA_WARP", "10.000", "-1.00", null).andExpect(status().isBadRequest());
        String cancelled = createOrder(customer, "KORA_WARP", "5.000", "850.00");
        asOwner(postJson("/api/v1/orders/" + cancelled + "/cancel", "{\"reason\":\"x\"}")).andExpect(status().isOk());
        create(manufacturer, "KORA_WARP", "5.000", "120.00", link(itemOf(cancelled), "5.000"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        // AC4: an inactive manufacturer 422; unknown job 404
        asOwner(post("/api/v1/parties/" + manufacturer + "/deactivate"))
                .andExpect(status().isOk());
        create(manufacturer, "KORA_WARP", "10.000", "120.00", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PARTY_INACTIVE"));
        asOwner(get("/api/v1/outsourcing-jobs/" + UUID.randomUUID())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from outsourcing_jobs", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-OUT-01 create non manufacturer returns422 invalid party role")
    void create_nonManufacturer_returns422InvalidPartyRole() throws Exception {
        create(data.supplier().id(), "KORA_WARP", "10.000", "120.00", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        assertThat(jdbc.queryForObject("select count(*) from outsourcing_jobs", Integer.class)).isZero();
        // the number is not used up by the refused request
        create(data.manufacturer().id(), "KORA_WARP", "10.000", "120.00", null)
                .andExpect(jsonPath("$.jobNumber").value(Matchers.endsWith("-0001")));
    }

    @Test
    @DisplayName("FR-OUT-02 FR-OUT-08 FR-OUT-09 issue moves raw silk to external wip and locks linked order")
    void issue_movesRawSilkToExternalWipAndLocksLinkedOrder() throws Exception {
        data.rawSilkInStock("300.000");
        UUID manufacturer = data.manufacturer().id();
        String order = confirmedOrder(data.customer().id(), "SILK_WARP", "200.000", "900.00");
        String id = body(create(manufacturer, "SILK_WARP", "200.000", "130.00", link(itemOf(order), "200.000")))
                .get("id").asString();

        // AC1: raw silk 100 left, 200 silk warp outside with the manufacturer
        asOwner(postJson("/api/v1/outsourcing-jobs/" + id + "/issue", "{\"issueDate\":\"" + today() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATERIAL_ISSUED"))
                .andExpect(jsonPath("$.issueDate").value(today().toString()))
                .andExpect(jsonPath("$.outsideWeightKg").value(200.000));
        assertThat(kg("RAW_SILK", "RAW_STOCK")).isEqualByComparingTo("100.000");
        assertThat(kg("SILK_WARP", "EXTERNAL_WIP")).isEqualByComparingTo("200.000");
        assertThat(jdbc.queryForObject("select count(*) from material_movements where movement_type = 'OUTSOURCE_ISSUE'"
                + " and reference_type = 'OUTSOURCING_JOB' and reference_id = ?::uuid", Integer.class, id)).isEqualTo(1);
        // FR-ORD-08: the linked order is in progress and locked
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        asOwner(postJson("/api/v1/orders/" + order + "/cancel", "{\"reason\":\"x\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_LOCKED"));
        // AC3: issued twice 409, nothing moves again
        asOwner(post("/api/v1/outsourcing-jobs/" + id + "/issue")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(kg("RAW_SILK", "RAW_STOCK")).isEqualByComparingTo("100.000");

        // AC2: 150 asked, 100 left -> 409, job stays CREATED, its linked order stays CONFIRMED
        String other = confirmedOrder(data.customer().id(), "SILK_WARP", "150.000", "900.00");
        String big = body(create(manufacturer, "SILK_WARP", "150.000", "130.00", link(itemOf(other), "150.000")))
                .get("id").asString();
        asOwner(post("/api/v1/outsourcing-jobs/" + big + "/issue")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        asOwner(get("/api/v1/outsourcing-jobs/" + big)).andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.issueDate").value(Matchers.nullValue()));
        asOwner(get("/api/v1/orders/" + other)).andExpect(jsonPath("$.status").value("CONFIRMED"));
        // FR-OUT-08 AC1: a job without an order issues and changes no order
        String free = body(create(manufacturer, "KORA_WARP", "100.000", "120.00", null)).get("id").asString();
        asOwner(post("/api/v1/outsourcing-jobs/" + free + "/issue")).andExpect(status().isOk());
        assertThat(kg("RAW_SILK", "RAW_STOCK")).isEqualByComparingTo("0.000");
        asOwner(get("/api/v1/orders/" + other)).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("FR-OUT-03 FR-OUT-09 receive moves warp to finished stock with wastage")
    void receive_movesWarpToFinishedStockWithWastage() throws Exception {
        data.rawSilkInStock("300.000");
        UUID manufacturer = data.manufacturer().id();
        String created = body(create(manufacturer, "KORA_WARP", "50.000", "120.00", null)).get("id").asString();
        // AC3: a CREATED job takes no receipt; AC4: received 0 is 400
        receive(created, "1.000", "0.000").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        String id = issuedJob(manufacturer, "200.000");
        receive(id, "0.000", "1.000").andExpect(status().isBadRequest());

        // AC1: 150 received with 3 wastage -> finished +150, EXTERNAL_WIP -153, PARTIALLY_RECEIVED
        receive(id, "150.000", "3.000").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.receivedWeightKg").value(150.000))
                .andExpect(jsonPath("$.outsideWeightKg").value(47.000))
                .andExpect(jsonPath("$.receivedDate").value(today().toString()))
                .andExpect(jsonPath("$.receipts.length()").value(1))
                .andExpect(jsonPath("$.receipts[0].wastageWeightKg").value(3.000))
                .andExpect(jsonPath("$.receipts[0].receiptMovementNumber").value(Matchers.startsWith("MOV-")))
                .andExpect(jsonPath("$.receipts[0].wastageMovementNumber").value(Matchers.startsWith("MOV-")));
        assertThat(kg("KORA_WARP", "FINISHED_STOCK")).isEqualByComparingTo("150.000");
        assertThat(kg("KORA_WARP", "EXTERNAL_WIP")).isEqualByComparingTo("47.000");
        assertThat(jdbc.queryForObject("select reason from material_movements where movement_type = 'WASTAGE'"
                + " and reference_id = ?::uuid", String.class, id)).isEqualTo("OUTSOURCING_LOSS");
        // AC2: 60 more with 47 outside -> 409, nothing changes
        receive(id, "60.000", "0.000").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSOURCE_RECEIPT_EXCEEDED"));
        assertThat(kg("KORA_WARP", "EXTERNAL_WIP")).isEqualByComparingTo("47.000");
        // FR-OUT-04: the rest (45 received, 2 loss) completes the job; zero wastage writes no wastage movement
        receive(id, "45.000", "2.000").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outsideWeightKg").value(0))
                .andExpect(jsonPath("$.payableAmount").value(23400.00))
                .andExpect(jsonPath("$.receipts.length()").value(2));
        receive(id, "1.000", "0.000").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSOURCE_RECEIPT_EXCEEDED"));
        String clean = issuedJob(manufacturer, "10.000");
        receive(clean, "10.000", "0.000").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.receipts[0].wastageMovementNumber").value(Matchers.nullValue()));
        assertThat(jdbc.queryForObject("select count(*) from material_movements where reference_id = ?::uuid",
                Integer.class, clean)).isEqualTo(2);
        // a receipt needs a key
        asOwner(postJson("/api/v1/outsourcing-jobs/" + id + "/receive", receiptBody("1.000", "0.000")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-OUT-11 receipts cannot be edited or deleted")
    void receipts_cannotBeEditedOrDeleted() throws Exception {
        data.rawSilkInStock("100.000");
        String id = issuedJob(data.manufacturer().id(), "100.000");
        receive(id, "50.000", "0.000").andExpect(status().isOk());
        String movement = jdbc.queryForObject("select id from material_movements where movement_type ="
                + " 'OUTSOURCE_RECEIPT' and reference_id = ?::uuid", String.class, id);

        // AC1: no route edits or deletes a receipt, its movement or the job's weights
        for (String path : java.util.List.of("/api/v1/outsourcing-jobs/" + id + "/receive",
                "/api/v1/outsourcing-jobs/" + id, "/api/v1/inventory/movements/" + movement)) {
            asOwner(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed());
            asOwner(delete(path)).andExpect(status().isMethodNotAllowed());
        }
        asOwner(patch("/api/v1/outsourcing-jobs/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"receivedWeightKg\":1.000}")).andExpect(status().isMethodNotAllowed());
        asOwner(get("/api/v1/outsourcing-jobs/" + id)).andExpect(jsonPath("$.receivedWeightKg").value(50.000));
    }

    @Test
    @DisplayName("FR-OUT-09 FR-PAY-07 receive replay with same key receives once")
    void receive_replayWithSameKey_receivesOnce() throws Exception {
        data.rawSilkInStock("100.000");
        String id = issuedJob(data.manufacturer().id(), "100.000");

        replayTwice("/api/v1/outsourcing-jobs/" + id + "/receive", receiptBody("40.000", "1.000"), 200);

        assertThat(kg("KORA_WARP", "FINISHED_STOCK")).isEqualByComparingTo("40.000");
        assertThat(jdbc.queryForObject("select count(*) from material_movements where movement_type ="
                + " 'OUTSOURCE_RECEIPT'", Integer.class)).isEqualTo(1);
        asOwner(get("/api/v1/outsourcing-jobs/" + id)).andExpect(jsonPath("$.receivedWeightKg").value(40.000))
                .andExpect(jsonPath("$.receipts.length()").value(1));
    }

    @Test
    @DisplayName("FR-OUT-07 get returns issued received remaining and payable")
    void get_returnsIssuedReceivedRemainingAndPayable() throws Exception {
        data.rawSilkInStock("300.000");
        UUID manufacturer = data.manufacturer().id();
        UUID other = data.party("Other weaver", java.util.List.of(com.nexora.parties.domain.PartyRole.MANUFACTURER),
                null, null).id();
        String order = confirmedOrder(data.customer().id(), "KORA_WARP", "200.000", "850.00");
        String id = body(create(manufacturer, "KORA_WARP", "200.000", "120.00", link(itemOf(order), "200.000")))
                .get("id").asString();
        asOwner(post("/api/v1/outsourcing-jobs/" + id + "/issue")).andExpect(status().isOk());
        receive(id, "150.000", "5.000").andExpect(status().isOk());
        create(other, "SILK_WARP", "20.000", "130.00", null).andExpect(status().isCreated());

        // issued, received, wastage, remaining outside, payable at the historical rate, receipts
        asOwner(get("/api/v1/outsourcing-jobs/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedWeightKg").value(200.000))
                .andExpect(jsonPath("$.receivedWeightKg").value(150.000))
                .andExpect(jsonPath("$.wastageWeightKg").value(5.000))
                .andExpect(jsonPath("$.outsideWeightKg").value(45.000))
                .andExpect(jsonPath("$.payableAmount").value(18000.00))
                .andExpect(jsonPath("$.allocatedAmount").value(0))
                .andExpect(jsonPath("$.outstandingAmount").value(18000.00))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.receipts[0].receivedWeightKg").value(150.000));
        // the order lists the job serving it
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.outsourcingJobs.length()").value(1))
                .andExpect(jsonPath("$.outsourcingJobs[0].id").value(id))
                .andExpect(jsonPath("$.outsourcingJobs[0].allocatedWeightKg").value(200.000));
        // AC1: list by manufacturer, without receipts; other filters
        asOwner(get("/api/v1/outsourcing-jobs?manufacturerId=" + manufacturer))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].outsideWeightKg").value(45.000))
                .andExpect(jsonPath("$.content[0].receipts").value(Matchers.nullValue()));
        asOwner(get("/api/v1/outsourcing-jobs?status=CREATED")).andExpect(jsonPath("$.totalElements").value(1));
        asOwner(get("/api/v1/outsourcing-jobs?orderId=" + order)).andExpect(jsonPath("$.totalElements").value(1));
        asOwner(get("/api/v1/outsourcing-jobs?from=" + today())).andExpect(jsonPath("$.totalElements").value(1));
        asOwner(get("/api/v1/outsourcing-jobs")).andExpect(jsonPath("$.totalElements").value(2));
        asOwner(get("/api/v1/outsourcing-jobs?sort=notes")).andExpect(status().isBadRequest());
        // AC2: unknown 404; AC3: no OUTSOURCING_MANAGE (STAFF) -> 403 on create
        asOwner(get("/api/v1/outsourcing-jobs/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(postJson("/api/v1/outsourcing-jobs", "{}")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-OUT-06 cancel created sets cancelled")
    void cancel_created_setsCancelled() throws Exception {
        data.rawSilkInStock("100.000");
        UUID manufacturer = data.manufacturer().id();
        String id = body(create(manufacturer, "KORA_WARP", "20.000", "120.00", null)).get("id").asString();

        asOwner(postJson("/api/v1/outsourcing-jobs/" + id + "/cancel", "{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        // AC1
        asOwner(postJson("/api/v1/outsourcing-jobs/" + id + "/cancel", "{\"reason\":\"Manufacturer unavailable\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.outsideWeightKg").value(0));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'OUTSOURCING_CANCELLED'"
                + " and entity_id = ?::uuid and reason = 'Manufacturer unavailable'", Integer.class, id)).isEqualTo(1);
        asOwner(post("/api/v1/outsourcing-jobs/" + id + "/issue")).andExpect(status().isConflict());
        // AC2: an issued job cannot be cancelled
        String issued = issuedJob(manufacturer, "30.000");
        asOwner(postJson("/api/v1/outsourcing-jobs/" + issued + "/cancel", "{\"reason\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(kg("RAW_SILK", "RAW_STOCK")).isEqualByComparingTo("70.000");
    }

    // A KORA_WARP job of this weight, created and issued (raw silk must be in stock).
    private String issuedJob(UUID manufacturer, String kg) throws Exception {
        String id = body(create(manufacturer, "KORA_WARP", kg, "120.00", null).andExpect(status().isCreated()))
                .get("id").asString();
        asOwner(post("/api/v1/outsourcing-jobs/" + id + "/issue")).andExpect(status().isOk());
        return id;
    }

    private ResultActions receive(String id, String received, String wastage) throws Exception {
        return asOwner(postWithKey("/api/v1/outsourcing-jobs/" + id + "/receive", receiptBody(received, wastage)));
    }

    private String receiptBody(String received, String wastage) {
        return """
                {"receiptDate":"%s","receivedWeightKg":%s,"wastageWeightKg":%s}""".formatted(today(), received, wastage);
    }

    private ResultActions create(UUID manufacturer, String product, String kg, String rate, String links)
            throws Exception {
        return asOwner(postJson("/api/v1/outsourcing-jobs", """
                {"manufacturerId":"%s","productType":"%s","issuedWeightKg":%s,"ratePerKg":%s%s}""".formatted(
                manufacturer, product, kg, rate, links == null ? "" : ",\"orderItems\":[" + links + "]")));
    }

    private static String link(String itemId, String kg) {
        return "{\"orderItemId\":\"" + itemId + "\",\"allocatedWeightKg\":" + kg + "}";
    }

    private String itemOf(String orderId) throws Exception {
        return body(asOwner(get("/api/v1/orders/" + orderId))).get("items").get(0).get("id").asString();
    }

    private BigDecimal kg(String product, String location) {
        return jdbc.queryForObject("select coalesce(sum(quantity_kg), 0) from inventory_balances where product_type = ?"
                + " and location = ?", BigDecimal.class, product, location);
    }
}

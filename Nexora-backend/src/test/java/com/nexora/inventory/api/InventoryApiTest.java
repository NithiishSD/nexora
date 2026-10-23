package com.nexora.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.application.MovementCommand;
import com.nexora.inventory.application.MovementResult;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

import tools.jackson.databind.JsonNode;

class InventoryApiTest extends AbstractApiTest {

    @Autowired
    InventoryService inventory;

    @Test
    @DisplayName("FR-INV-07 FR-INV-16 summary returns balances totals and cumulative wastage")
    void summary_returnsBalancesTotalsAndCumulativeWastage() throws Exception {
        // AC3: an empty database gives every legal pair with 0.000
        as(get("/api/v1/inventory/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.length()").value(8))
                .andExpect(jsonPath("$.rawSilkAvailableKg").value(0.0))
                .andExpect(jsonPath("$.unit").value("kg"));

        data.rawSilkInStock("21.500");
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "55.500");
        data.openingStock(ProductType.SILK_WARP, StockLocation.EXTERNAL_WIP, "12.000");
        record(wastage(ProductType.RAW_SILK, "1.500", LocalDate.of(2026, 9, 5)));

        // AC1: the balances and the totals, in kg with 3 decimals
        String body = as(get("/api/v1/inventory/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vudaWarpKg").value(0.0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"rawSilkAvailableKg\":20.000", "\"finishedKoraWarpKg\":55.500",
                "\"finishedSilkWarpKg\":0.000", "\"externalWipKg\":12.000", "\"internalWipKg\":0.000",
                "{\"productType\":\"RAW_SILK\",\"location\":\"RAW_STOCK\",\"quantityKg\":20.000",
                // FR-INV-16: wastage is visible on its own, never merged into available stock
                "{\"productType\":\"RAW_SILK\",\"quantityKg\":1.500}");
        // AC2: a token without INVENTORY_VIEW is refused (STAFF holds it, a minted token without it does not)
        mockMvc.perform(get("/api/v1/inventory/summary").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(
                        tokens.mint(UUID.randomUUID(), java.util.List.of("PARTY_VIEW"), java.time.Instant.now(),
                                java.time.Duration.ofMinutes(5)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("FR-INV-08 movements filtered by product location and date returns page")
    void movements_filteredByProductLocationAndDate_returnsPage() throws Exception {
        data.rawSilkInStock("100.000");                                           // dated 2026-01-01
        record(receipt("95.000", LocalDate.of(2026, 9, 10)));
        record(receipt("10.000", LocalDate.of(2026, 10, 2)));
        record(wastage(ProductType.RAW_SILK, "2.000", LocalDate.of(2026, 9, 20)));
        data.openingStock(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK, "5.000");

        // AC1: September receipts only
        as(get("/api/v1/inventory/movements?movementType=RECEIPT&from=2026-09-01&to=2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].movementType").value("RECEIPT"))
                .andExpect(jsonPath("$.content[0].fromLocation").value("SUPPLIER"))
                .andExpect(jsonPath("$.content[0].referenceType").value("MATERIAL_RECEIPT"))
                .andExpect(jsonPath("$.content[0].referenceNumber").value("RCT-TEST-0001"));
        // product + location (either side) + date range, paged
        as(get("/api/v1/inventory/movements?productType=RAW_SILK&location=RAW_STOCK&from=2026-09-01&size=1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(1));
        // AC3: in minus out of RAW_SILK/RAW_STOCK over the whole ledger equals the balance
        JsonNode all = json(as(get("/api/v1/inventory/movements?productType=RAW_SILK&location=RAW_STOCK&size=100")));
        BigDecimal net = BigDecimal.ZERO;
        for (JsonNode m : all.get("content")) {
            BigDecimal kg = m.get("quantityKg").decimalValue();
            net = "RAW_STOCK".equals(m.get("toLocation").asString()) ? net.add(kg) : net.subtract(kg);
        }
        assertThat(net).isEqualByComparingTo("203.000");
        assertThat(json(as(get("/api/v1/inventory/summary"))).get("rawSilkAvailableKg").decimalValue())
                .isEqualByComparingTo(net);
        // one movement by id
        String id = all.get("content").get(0).get("id").asString();
        as(get("/api/v1/inventory/movements/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        // AC2: a backwards date range is a 400 naming the field; an unknown sort property too
        as(get("/api/v1/inventory/movements?from=2026-10-01&to=2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        as(get("/api/v1/inventory/movements?sort=quantityKg,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    @DisplayName("FR-INV-01 post movement route does not exist")
    void postMovement_routeDoesNotExist() throws Exception {
        MovementResult existing = data.rawSilkInStock("10.000");

        // AC2: nobody can create a movement directly, not even the owner
        as(post("/api/v1/inventory/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productType\":\"RAW_SILK\",\"toLocation\":\"RAW_STOCK\",\"quantityKg\":999}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        // AC3: and nobody can edit or delete one
        String path = "/api/v1/inventory/movements/" + existing.id();
        for (MockHttpServletRequestBuilder call : java.util.List.of(
                put(path).contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch(path).contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete(path))) {
            as(call).andExpect(status().isMethodNotAllowed());
        }
        assertThat(jdbc.queryForObject("select count(*) from material_movements", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity_kg from inventory_balances", BigDecimal.class))
                .isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("FR-PROC-20 summary exposes raw silk available for purchase screen")
    void summary_exposesRawSilkAvailableForPurchaseScreen() throws Exception {
        // POST-MVP boundary: the purchase screen shows current raw silk from the summary; there is no stock-hint
        // endpoint and no threshold in the first release
        data.rawSilkInStock("40.000");

        assertThat(json(as(get("/api/v1/inventory/summary"))).get("rawSilkAvailableKg").decimalValue())
                .isEqualByComparingTo("40.000");
        as(get("/api/v1/purchases/stock-hint")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
    }

    @Test
    @DisplayName("FR-INV-18 summary has no lot or cost fields in first release")
    void summary_hasNoThresholdOrLotFieldsInFirstRelease() throws Exception {
        data.rawSilkInStock("40.000");

        String body = as(get("/api/v1/inventory/summary")).andReturn().getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("lot").doesNotContain("cost");
        // the threshold endpoint is POST-MVP: not there
        as(put("/api/v1/inventory/thresholds/RAW_SILK").contentType(MediaType.APPLICATION_JSON)
                .content("{\"thresholdKg\":50.000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-INV-11 wastage records movement with reason")
    void wastage_recordsMovementWithReason() throws Exception {
        data.rawSilkInStock("20.000");

        // AC1: 1.500 kg lost to moisture -> 18.500 left, and the wastage is visible on its own
        as(postJson("/api/v1/inventory/wastage", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","quantityKg":1.500,
                 "movementDate":"2026-10-06","reason":"moisture loss"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/inventory/movements/")))
                .andExpect(jsonPath("$.movementType").value("WASTAGE"))
                .andExpect(jsonPath("$.toLocation").value("WASTAGE"))
                .andExpect(jsonPath("$.reason").value("moisture loss"))
                .andExpect(jsonPath("$.createdBy").isNotEmpty());
        assertThat(rawSilk()).isEqualByComparingTo("18.500");
        assertThat(json(as(get("/api/v1/inventory/summary"))).get("wastageToDateKg").get(0).get("quantityKg")
                .decimalValue()).isEqualByComparingTo("1.500");

        // AC2: a blank reason is 400 naming the field
        as(postJson("/api/v1/inventory/wastage", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","quantityKg":1.000,
                 "movementDate":"2026-10-06","reason":"  "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        // wastage can only start from raw stock or work in progress
        as(postJson("/api/v1/inventory/wastage", """
                {"productType":"SILK_WARP","fromLocation":"FINISHED_STOCK","quantityKg":1.000,
                 "movementDate":"2026-10-06","reason":"torn"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("fromLocation"));
        // a typing mistake in the year is caught (business date window)
        as(postJson("/api/v1/inventory/wastage", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","quantityKg":1.000,
                 "movementDate":"2206-10-06","reason":"moisture loss"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("movementDate"));
        // AC3: more than is there -> 409, nothing changes
        as(postJson("/api/v1/inventory/wastage", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","quantityKg":20.000,
                 "movementDate":"2026-10-06","reason":"moisture loss"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        assertThat(rawSilk()).isEqualByComparingTo("18.500");
        assertThat(movementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-INV-12 adjustment without permission returns403")
    void adjustment_withoutPermission_returns403() throws Exception {
        data.rawSilkInStock("20.000");

        // AC4: STAFF can look at stock but not change it
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADJUST_UP))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(rawSilk()).isEqualByComparingTo("20.000");
        assertThat(movementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-INV-12 NFR-REL-01 adjustment below zero returns409 insufficient inventory")
    void adjustment_belowZero_returns409InsufficientInventory() throws Exception {
        data.rawSilkInStock("20.000");

        // AC1: a count found 3 kg more: SUPPLIER -> RAW_STOCK, the reason stays on the movement
        as(postJson("/api/v1/inventory/adjustments", ADJUST_UP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movementType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.reason").value("stock count 2026-10-01"));
        assertThat(rawSilk()).isEqualByComparingTo("23.000");

        // AC3: taking 30 kg out of 23 kg is refused even for the owner (ADR-026)
        as(postJson("/api/v1/inventory/adjustments", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","toLocation":"WASTAGE","quantityKg":30.000,
                 "movementDate":"2026-10-01","reason":"stock count"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"))
                .andExpect(jsonPath("$.detail").value("Only 23.000 kg of RAW_SILK available in RAW_STOCK."));
        assertThat(rawSilk()).isEqualByComparingTo("23.000");

        // AC2: a missing reason, FR-INV-03 AC2: two outside locations, and a product the location never holds
        as(postJson("/api/v1/inventory/adjustments", """
                {"productType":"RAW_SILK","fromLocation":"SUPPLIER","toLocation":"RAW_STOCK","quantityKg":1.000,
                 "movementDate":"2026-10-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        as(postJson("/api/v1/inventory/adjustments", """
                {"productType":"RAW_SILK","fromLocation":"SUPPLIER","toLocation":"CUSTOMER","quantityKg":1.000,
                 "movementDate":"2026-10-01","reason":"x"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        as(postJson("/api/v1/inventory/adjustments", """
                {"productType":"SILK_WARP","fromLocation":"CUSTOMER","toLocation":"RAW_STOCK","quantityKg":1.000,
                 "movementDate":"2026-10-01","reason":"x"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_FOR_OPERATION"));
        assertThat(movementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-INV-13 opening balance recorded once per product and location")
    void openingBalance_recordedOncePerProductAndLocation() throws Exception {
        String opening = """
                {"productType":"RAW_SILK","location":"RAW_STOCK","quantityKg":120.000,"movementDate":"2026-10-01"}""";

        // AC1: 120 kg counted at go-live; an opening movement, no purchase and no money
        as(postJson("/api/v1/inventory/opening-balances", opening))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movementType").value("OPENING_BALANCE"))
                .andExpect(jsonPath("$.fromLocation").value(nullValue()))
                .andExpect(jsonPath("$.referenceType").value(nullValue()));
        assertThat(rawSilk()).isEqualByComparingTo("120.000");

        // AC2: a second opening entry for the same product and location is refused
        as(postJson("/api/v1/inventory/opening-balances", opening))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
        assertThat(rawSilk()).isEqualByComparingTo("120.000");
        // another product in the same place is a separate opening entry
        as(postJson("/api/v1/inventory/opening-balances", """
                {"productType":"VUDA_WARP","location":"RAW_STOCK","quantityKg":5.000,"movementDate":"2026-10-01"}"""))
                .andExpect(status().isCreated());
        // only internal locations, and only products the location holds
        as(postJson("/api/v1/inventory/opening-balances", """
                {"productType":"RAW_SILK","location":"CUSTOMER","quantityKg":5.000,"movementDate":"2026-10-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("location"));
        as(postJson("/api/v1/inventory/opening-balances", """
                {"productType":"RAW_SILK","location":"FINISHED_STOCK","quantityKg":5.000,"movementDate":"2026-10-01"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_FOR_OPERATION"));
        assertThat(movementCount()).isEqualTo(2);
    }

    // ---- helpers -------------------------------------------------------------------------

    private static final String ADJUST_UP = """
            {"productType":"RAW_SILK","fromLocation":"SUPPLIER","toLocation":"RAW_STOCK","quantityKg":3.000,
             "movementDate":"2026-10-01","reason":"stock count 2026-10-01"}""";


    private BigDecimal rawSilk() {
        return jdbc.queryForObject("select quantity_kg from inventory_balances where product_type = 'RAW_SILK'"
                + " and location = 'RAW_STOCK'", BigDecimal.class);
    }

    private int movementCount() {
        return jdbc.queryForObject("select count(*) from material_movements", Integer.class);
    }

    private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner())));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("FR-INV-14 external wip per manufacturer and job")
    void externalWip_perManufacturerAndJob() throws Exception {
        data.rawSilkInStock("400.000");
        UUID anand = data.party("Anand Weaving", java.util.List.of(com.nexora.parties.domain.PartyRole.MANUFACTURER),
                null, null).id();
        UUID bala = data.party("Bala Twisting", java.util.List.of(com.nexora.parties.domain.PartyRole.MANUFACTURER),
                null, null).id();
        String a1 = job(anand, "KORA_WARP", "200.000", true);
        String a2 = job(anand, "SILK_WARP", "50.000", true);
        job(bala, "KORA_WARP", "100.000", true);
        job(bala, "KORA_WARP", "30.000", false);                     // CREATED: nothing issued
        asOwner(postWithKey("/api/v1/outsourcing-jobs/" + a1 + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":150.000,"wastageWeightKg":5.000}""".formatted(today())))
                .andExpect(status().isOk());

        // AC1: 200 issued, 150 received, 5 wastage -> 45 outside; AC3: the CREATED job is not there
        asOwner(get("/api/v1/inventory/external-wip")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutsideKg").value(195.000))
                .andExpect(jsonPath("$.manufacturers.length()").value(2))
                .andExpect(jsonPath("$.manufacturers[0].manufacturer.id").value(anand.toString()))
                .andExpect(jsonPath("$.manufacturers[0].issuedKg").value(250.000))
                .andExpect(jsonPath("$.manufacturers[0].outsideKg").value(95.000))
                .andExpect(jsonPath("$.manufacturers[0].jobs.length()").value(2))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].jobId").value(a1))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].receivedKg").value(150.000))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].wastageKg").value(5.000))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].outsideKg").value(45.000))
                .andExpect(jsonPath("$.manufacturers[1].jobs.length()").value(1))
                .andExpect(jsonPath("$.manufacturers[1].outsideKg").value(100.000));
        // AC2: the jobs add up to the EXTERNAL_WIP balance
        BigDecimal balance = jdbc.queryForObject("select sum(quantity_kg) from inventory_balances where location ="
                + " 'EXTERNAL_WIP'", BigDecimal.class);
        assertThat(balance).isEqualByComparingTo("195.000");
        // one manufacturer; a completed job leaves the list; unknown manufacturer 404
        asOwner(get("/api/v1/inventory/external-wip?manufacturerId=" + anand))
                .andExpect(jsonPath("$.totalOutsideKg").value(95.000))
                .andExpect(jsonPath("$.manufacturers.length()").value(1));
        asOwner(postWithKey("/api/v1/outsourcing-jobs/" + a2 + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":50.000,"wastageWeightKg":0.000}""".formatted(today())))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        asOwner(get("/api/v1/inventory/external-wip?manufacturerId=" + anand))
                .andExpect(jsonPath("$.manufacturers[0].jobs.length()").value(1));
        asOwner(get("/api/v1/inventory/external-wip?manufacturerId=" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private String job(UUID manufacturer, String product, String kg, boolean issue) throws Exception {
        String id = body(asOwner(postJson("/api/v1/outsourcing-jobs", """
                {"manufacturerId":"%s","productType":"%s","issuedWeightKg":%s,"ratePerKg":120.00}""".formatted(
                manufacturer, product, kg))).andExpect(status().isCreated())).get("id").asString();
        if (issue) {
            asOwner(post("/api/v1/outsourcing-jobs/" + id + "/issue")).andExpect(status().isOk());
        }
        return id;
    }

    private void record(MovementCommand command) {
        new TransactionTemplate(transactionManager).execute(s -> inventory.recordMovement(command));
    }

    private static MovementCommand receipt(String kg, LocalDate date) {
        return new MovementCommand(ProductType.RAW_SILK, null, MovementType.RECEIPT, StockLocation.SUPPLIER,
                StockLocation.RAW_STOCK, new BigDecimal(kg), date, MovementReferenceType.MATERIAL_RECEIPT,
                UUID.randomUUID(), "RCT-TEST-0001", null, null);
    }

    private static MovementCommand wastage(ProductType product, String kg, LocalDate date) {
        return new MovementCommand(product, null, MovementType.WASTAGE, StockLocation.RAW_STOCK, StockLocation.WASTAGE,
                new BigDecimal(kg), date, null, null, null, "moisture loss", null);
    }
}

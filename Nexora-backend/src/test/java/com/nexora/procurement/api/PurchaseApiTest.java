package com.nexora.procurement.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nexora.parties.api.dto.PartyResponse;
import com.nexora.parties.domain.PartyRole;
import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

import tools.jackson.databind.JsonNode;

class PurchaseApiTest extends AbstractApiTest {

    @Autowired
    Clock clock;

    @Test
    @DisplayName("FR-PROC-01 FR-PROC-02 create with valid request returns201 with purchase number")
    void create_withValidRequest_returns201WithPurchaseNumber() throws Exception {
        PartyResponse supplier = data.supplier();                     // default credit days 10
        String year = String.valueOf(Year.now(clock).getValue());

        // AC1: 250 kg at 800.00 on credit -> ORDERED, credit days from the supplier, PUR number
        as(postJson("/api/v1/purchases", purchaseJson(supplier.id(), "250.000", "800.00", "CREDIT", null)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/purchases/")))
                .andExpect(jsonPath("$.purchaseNumber").value("PUR-" + year + "-0001"))
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.creditDays").value(10))
                .andExpect(jsonPath("$.dueDate").value("2026-10-11"))
                .andExpect(jsonPath("$.productType").value("RAW_SILK"))
                .andExpect(jsonPath("$.supplier.partyCode").value(supplier.partyCode()))
                .andExpect(jsonPath("$.receipts.length()").value(0))
                .andExpect(jsonPath("$.createdBy").isNotEmpty());
        // the next purchase gets the next number
        as(postJson("/api/v1/purchases", purchaseJson(supplier.id(), "10.000", "800.00", "CREDIT", 5)))
                .andExpect(jsonPath("$.purchaseNumber").value("PUR-" + year + "-0002"))
                .andExpect(jsonPath("$.creditDays").value(5));

        // AC2: weight 0; AC5: negative rate -> 400 naming the field, nothing stored
        as(postJson("/api/v1/purchases", purchaseJson(supplier.id(), "0", "800.00", "CREDIT", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orderedWeightKg"));
        as(postJson("/api/v1/purchases", purchaseJson(supplier.id(), "10.000", "-1.00", "CREDIT", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ratePerKg"));
        // AC4: an inactive supplier
        PartyResponse inactive = data.supplier();
        mockMvc.perform(post("/api/v1/parties/" + inactive.id() + "/deactivate").header(HttpHeaders.AUTHORIZATION,
                AuthTokens.bearer(tokens.owner())));
        as(postJson("/api/v1/purchases", purchaseJson(inactive.id(), "10.000", "800.00", "CREDIT", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PARTY_INACTIVE"));
        // FR-PROC-17 AC2: warp is never purchased
        as(postJson("/api/v1/purchases", """
                {"supplierId":"%s","productType":"SILK_WARP","purchaseDate":"2026-10-01","orderedWeightKg":1.000,
                 "ratePerKg":1.00,"paymentType":"CASH"}""".formatted(supplier.id())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_FOR_OPERATION"));
        assertThat(purchaseCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-PROC-01 FR-PARTY-12 create with non supplier returns422 invalid party role")
    void create_withNonSupplier_returns422InvalidPartyRole() throws Exception {
        PartyResponse customer = data.customer();

        as(postJson("/api/v1/purchases", purchaseJson(customer.id(), "250.000", "800.00", "CREDIT", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        as(postJson("/api/v1/purchases", purchaseJson(UUID.randomUUID(), "250.000", "800.00", "CREDIT", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(purchaseCount()).isZero();
    }

    @Test
    @DisplayName("FR-PROC-03 create response value computed by backend")
    void create_responseValueComputedByBackend() throws Exception {
        PartyResponse supplier = data.supplier();

        // AC2: a client-sent value is ignored; the backend computes 250.000 x 800.00
        String body = as(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"2026-10-01","orderedWeightKg":250.000,"ratePerKg":800.00,
                 "paymentType":"CREDIT","orderedValue":1.00,"status":"FULLY_RECEIVED","dueDate":"2030-01-01"}"""
                .formatted(supplier.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"orderedValue\":200000.00", "\"ratePerKg\":800.00", "\"orderedWeightKg\":250.000",
                "\"payableAmount\":0.00", "\"remainingWeightKg\":250.000");
        // AC1: and it is the same when the purchase is fetched
        String id = jsonMapper.readTree(body).get("id").asString();
        assertThat(as(get("/api/v1/purchases/" + id)).andReturn().getResponse().getContentAsString())
                .contains("\"orderedValue\":200000.00");
        // AC3: four decimals of weight is rejected, not rounded
        as(postJson("/api/v1/purchases", purchaseJson(supplier.id(), "12.3456", "800.00", "CREDIT", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orderedWeightKg"));
    }

    @Test
    @DisplayName("FR-PROC-04 create cash purchase due date equals purchase date")
    void create_cashPurchase_dueDateEqualsPurchaseDate() throws Exception {
        PartyResponse supplier = data.supplier();

        // AC2: CASH -> due on the purchase date, 0 credit days (the supplier's default does not apply)
        as(postJson("/api/v1/purchases", purchaseJson(supplier.id(), "10.000", "800.00", "CASH", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditDays").value(0))
                .andExpect(jsonPath("$.dueDate").value("2026-10-01"));
        // credit days with CASH, and AC3: negative credit days -> 400 on creditDays
        as(postJson("/api/v1/purchases", purchaseJson(supplier.id(), "10.000", "800.00", "CASH", 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("creditDays"));
        as(postJson("/api/v1/purchases", purchaseJson(supplier.id(), "10.000", "800.00", "CREDIT", -3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("creditDays"));
        // a supplier without default credit days gives 0 on credit too
        PartyResponse noTerms = data.party("Plain supplier", List.of(PartyRole.SUPPLIER), null, null);
        as(postJson("/api/v1/purchases", purchaseJson(noTerms.id(), "10.000", "800.00", "CREDIT", null)))
                .andExpect(jsonPath("$.creditDays").value(0))
                .andExpect(jsonPath("$.dueDate").value("2026-10-01"));
    }

    @Test
    @DisplayName("FR-PROC-05 patch rate or weight is rejected")
    void patch_rateOrWeight_isRejected() throws Exception {
        String id = createPurchase(data.supplier().id(), "250.000", "800.00");

        // AC1: notes and bill reference may change; rate and weight stay
        as(patchJson("/api/v1/purchases/" + id, "{\"notes\":\"Bill received late\",\"billReference\":\"SB-77\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Bill received late"))
                .andExpect(jsonPath("$.billReference").value("SB-77"))
                .andExpect(jsonPath("$.ratePerKg").value(800.0));
        // an empty string clears, a missing field keeps
        as(patchJson("/api/v1/purchases/" + id, "{\"billReference\":\"\"}"))
                .andExpect(jsonPath("$.billReference").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.notes").value("Bill received late"));
        // AC2: a patch with ratePerKg (or the weight) is a 400 naming the field, and nothing changes
        as(patchJson("/api/v1/purchases/" + id, "{\"ratePerKg\":700.00,\"notes\":\"cheaper\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("ratePerKg"));
        as(patchJson("/api/v1/purchases/" + id, "{\"orderedWeightKg\":1.000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orderedWeightKg"));
        assertThat(jdbc.queryForObject("select rate_per_kg from purchases", BigDecimal.class)).isEqualByComparingTo("800.00");
        assertThat(jdbc.queryForObject("select notes from purchases", String.class)).isEqualTo("Bill received late");
        // there is no PUT that could rewrite the purchase
        as(put("/api/v1/purchases/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("FR-PROC-06 get returns receipts returns and derived weights")
    void get_returnsReceiptsReturnsAndDerivedWeights() throws Exception {
        String id = createPurchase(data.supplier().id(), "250.000", "800.00");

        // a new purchase: nothing received yet, everything still to come, nothing payable (FR-PROC-15 AC3)
        String body = as(get("/api/v1/purchases/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.receipts.length()").value(0))
                .andExpect(jsonPath("$.returns.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"receivedWeightKg\":0.000", "\"acceptedWeightKg\":0.000",
                "\"rejectedWeightKg\":0.000", "\"returnedWeightKg\":0.000", "\"remainingWeightKg\":250.000",
                "\"payableAmount\":0.00");
        // AC1: one receipt of 100 kg (95 accepted) and a return of 10 kg -> derived weights, receipts and returns
        receive(id, "100.000", "95.000", "5.000").andExpect(status().isCreated());
        asOwner(postWithKey("/api/v1/purchases/" + id + "/returns",
                "{\"returnDate\":\"2026-10-05\",\"returnedWeightKg\":10.000,\"reason\":\"Moisture damage\"}"))
                .andExpect(status().isCreated());
        body = as(get("/api/v1/purchases/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.receipts.length()").value(1))
                .andExpect(jsonPath("$.returns.length()").value(1))
                .andExpect(jsonPath("$.receipts[0].rejectedWeightKg").value(5.0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"receivedWeightKg\":100.000", "\"acceptedWeightKg\":95.000",
                "\"rejectedWeightKg\":5.000", "\"returnedWeightKg\":10.000", "\"remainingWeightKg\":150.000",
                // FR-PROC-15: (95 - 10) x 800.00
                "\"payableAmount\":68000.00");
        // AC2: unknown id; AC3: no PURCHASE_VIEW (STAFF)
        as(get("/api/v1/purchases/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/purchases/" + id).header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("FR-PROC-07 list filtered by supplier status and date range")
    void list_filteredBySupplierStatusAndDateRange() throws Exception {
        UUID kumar = data.supplier().id();
        UUID devi = data.supplier().id();
        createPurchaseOn(kumar, "2026-08-15", "10.000");
        String sept = createPurchaseOn(kumar, "2026-09-10", "20.000");
        createPurchaseOn(kumar, "2026-09-25", "30.000");
        createPurchaseOn(devi, "2026-09-12", "40.000");
        createPurchaseOn(kumar, "2026-10-02", "50.000");

        // AC1: September only, for one supplier, newest first by default
        as(get("/api/v1/purchases?supplierId=" + kumar + "&from=2026-09-01&to=2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].purchaseDate").value("2026-09-25"))
                .andExpect(jsonPath("$.content[1].id").value(sept))
                .andExpect(jsonPath("$.content[1].supplier.id").value(kumar.toString()))
                .andExpect(jsonPath("$.content[1].orderedValue").value(16000.0))
                .andExpect(jsonPath("$.content[1].receivedWeightKg").value(0.0));
        // status filter and sort by number ascending
        as(get("/api/v1/purchases?status=ORDERED&sort=purchaseNumber,asc&size=2"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content[0].purchaseNumber").value(startsWith("PUR-")));
        as(get("/api/v1/purchases?status=CANCELLED")).andExpect(jsonPath("$.totalElements").value(0));
        // AC2: from later than to; an unknown sort property; an unknown status value
        as(get("/api/v1/purchases?from=2026-10-01&to=2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        as(get("/api/v1/purchases?sort=ratePerKg,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        as(get("/api/v1/purchases?status=LOST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("FR-PROC-12 cancel without receipts sets cancelled")
    void cancel_withoutReceipts_setsCancelled() throws Exception {
        String open = createPurchase(data.supplier().id(), "250.000", "800.00");

        // AC1: an ORDERED purchase without receipts is cancelled and kept
        as(postJson("/api/v1/purchases/" + open + "/cancel", "{\"reason\":\"Supplier could not supply\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.remainingWeightKg").value(0.0))
                .andExpect(jsonPath("$.payableAmount").value(0.0));
        assertThat(purchaseCount()).isEqualTo(1);
        // AC3: cancelling again; FR-PROC-11 AC2: receiving into a cancelled purchase
        as(postJson("/api/v1/purchases/" + open + "/cancel", "{\"reason\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        receive(open, "10.000", "10.000", "0.000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(jdbc.queryForObject("select count(*) from material_receipts", Integer.class)).isZero();

        // AC2: a purchase with a receipt cannot be cancelled
        String received = createPurchase(data.supplier().id(), "250.000", "800.00");
        receive(received, "10.000", "10.000", "0.000").andExpect(status().isCreated());
        as(postJson("/api/v1/purchases/" + received + "/cancel", "{\"reason\":\"too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        // a reason is required
        as(postJson("/api/v1/purchases/" + received + "/cancel", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        as(get("/api/v1/purchases/" + received)).andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"));
    }

    @Test
    @DisplayName("FR-PROC-18 delete is not supported")
    void delete_isNotSupported() throws Exception {
        String id = createPurchase(data.supplier().id(), "250.000", "800.00");

        as(delete("/api/v1/purchases/" + id))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        assertThat(purchaseCount()).isEqualTo(1);
    }

    // ---- helpers -------------------------------------------------------------------------

    private String createPurchase(UUID supplierId, String kg, String rate) throws Exception {
        JsonNode body = jsonMapper.readTree(as(postJson("/api/v1/purchases",
                purchaseJson(supplierId, kg, rate, "CREDIT", null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return body.get("id").asString();
    }

    private String createPurchaseOn(UUID supplierId, String date, String kg) throws Exception {
        String rate = "800.00";
        JsonNode body = jsonMapper.readTree(as(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":%s,"ratePerKg":%s,"paymentType":"CREDIT"}"""
                .formatted(supplierId, date, kg, rate)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return body.get("id").asString();
    }

    static String purchaseJson(UUID supplierId, String kg, String rate, String paymentType, Integer creditDays) {
        return """
                {"supplierId":"%s","purchaseDate":"2026-10-01","orderedWeightKg":%s,"ratePerKg":%s,
                 "paymentType":"%s"%s}""".formatted(supplierId, kg, rate, paymentType,
                creditDays == null ? "" : ",\"creditDays\":" + creditDays);
    }

    private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner())));
    }


    private static MockHttpServletRequestBuilder patchJson(String path, String body) {
        return patch(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int purchaseCount() {
        return jdbc.queryForObject("select count(*) from purchases", Integer.class);
    }
}

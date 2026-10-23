package com.nexora.procurement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nexora.support.AbstractApiTest;

import tools.jackson.databind.JsonNode;

class SupplierReturnApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PROC-19 return replay with same key creates one return")
    void return_replayWithSameKey_createsOneReturn() throws Exception {
        String purchase = createPurchase(data.supplier().id(), "RAW_SILK", "100.000", "800.00");
        receive(purchase, "100.000", "100.000", "0.000").andExpect(status().isCreated());

        replayTwice("/api/v1/purchases/" + purchase + "/returns", """
                {"returnDate":"2026-10-04","returnedWeightKg":10.000,"reason":"knots"}""", 201);

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from supplier_returns",
                Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select quantity_kg from inventory_balances"
                + " where product_type = 'RAW_SILK' and location = 'RAW_STOCK'", java.math.BigDecimal.class))
                .isEqualByComparingTo("90.000");
    }

    @Test
    @DisplayName("FR-PROC-13 FR-PROC-14 return creates purchase return movement receipt unchanged")
    void return_createsPurchaseReturnMovement_receiptUnchanged() throws Exception {
        String purchase = createPurchase(data.supplier().id(), "RAW_SILK", "250.000", "800.00");
        String receiptId = body(receive(purchase, "100.000", "100.000", "0.000")).get("receipts").get(0)
                .get("id").asString();

        // FR-PROC-13 AC1: 100 accepted and in stock, 59 returned -> stock 41, returned total 59
        JsonNode body = body(returnKg(purchase, "59.000")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/purchases/" + purchase + "/returns/")))
                .andExpect(jsonPath("$.returnedWeightKg").value(59.0))
                .andExpect(jsonPath("$.returns[0].returnNumber").value(startsWith("RTN-")))
                .andExpect(jsonPath("$.returns[0].reason").value("Moisture damage"))
                .andExpect(jsonPath("$.returns[0].movementNumber").value(startsWith("MOV-")))
                // FR-PROC-14 AC1: the receipt keeps its figures
                .andExpect(jsonPath("$.receipts[0].receivedWeightKg").value(100.0))
                .andExpect(jsonPath("$.receipts[0].acceptedWeightKg").value(100.0))
                // FR-PROC-15: payable is (accepted - returned) x rate
                .andExpect(jsonPath("$.payableAmount").value(32800.0)));
        assertThat(rawSilk()).isEqualByComparingTo("41.000");
        JsonNode supplierReturn = body.get("returns").get(0);
        Map<String, Object> movement = jdbc.queryForMap(
                "select * from material_movements where movement_type = 'PURCHASE_RETURN'");
        assertThat(movement).containsEntry("from_location", "RAW_STOCK").containsEntry("to_location", "RETURNED")
                .containsEntry("reference_type", "SUPPLIER_RETURN")
                .containsEntry("reference_id", UUID.fromString(supplierReturn.get("id").asString()))
                .containsEntry("reference_number", supplierReturn.get("returnNumber").asString());
        asOwner(get("/api/v1/purchases/" + purchase + "/returns/" + supplierReturn.get("id").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedWeightKg").value(59.0));
        // a return id of another purchase is not found here
        String other = createPurchase(data.supplier().id(), "RAW_SILK", "10.000", "800.00");
        asOwner(get("/api/v1/purchases/" + other + "/returns/" + supplierReturn.get("id").asString()))
                .andExpect(status().isNotFound());

        // FR-PROC-14 AC2: receipts and returns cannot be edited or deleted
        for (String path : List.of("/api/v1/purchases/" + purchase + "/receipts/" + receiptId,
                "/api/v1/purchases/" + purchase + "/returns/" + supplierReturn.get("id").asString())) {
            for (MockHttpServletRequestBuilder call : List.of(
                    put(path).contentType(MediaType.APPLICATION_JSON).content("{}"),
                    patch(path).contentType(MediaType.APPLICATION_JSON).content("{}"), delete(path))) {
                asOwner(call).andExpect(status().isMethodNotAllowed());
            }
        }
        assertThat(jdbc.queryForObject("select received_weight_kg from material_receipts", BigDecimal.class))
                .isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("FR-PROC-13 return more than available returns409 insufficient inventory")
    void return_moreThanAvailable_returns409InsufficientInventory() throws Exception {
        String purchase = createPurchase(data.supplier().id(), "RAW_SILK", "250.000", "800.00");
        receive(purchase, "100.000", "100.000", "0.000").andExpect(status().isCreated());
        returnKg(purchase, "60.000").andExpect(status().isCreated());

        // AC2: 100 accepted, 60 returned -> only 40 returnable from this purchase
        returnKg(purchase, "50.000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("40.000")));

        // AC3: still 40 returnable, but only 20 kg left in stock (the rest was lost) -> 409, stock unchanged
        asOwner(postJson("/api/v1/inventory/wastage", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","quantityKg":20.000,
                 "movementDate":"2026-10-04","reason":"moisture loss"}""")).andExpect(status().isCreated());
        returnKg(purchase, "30.000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        assertThat(rawSilk()).isEqualByComparingTo("20.000");
        assertThat(jdbc.queryForObject("select count(*) from supplier_returns", Integer.class)).isEqualTo(1);

        // AC4: weight 0 and a blank reason are 400
        returnKg(purchase, "0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("returnedWeightKg"));
        asOwner(postWithKey("/api/v1/purchases/" + purchase + "/returns", """
                {"returnDate":"2026-10-05","returnedWeightKg":1.000,"reason":" "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        // a cancelled purchase has nothing to return
        String cancelled = createPurchase(data.supplier().id(), "RAW_SILK", "10.000", "800.00");
        asOwner(postJson("/api/v1/purchases/" + cancelled + "/cancel", "{\"reason\":\"not needed\"}"))
                .andExpect(status().isOk());
        returnKg(cancelled, "1.000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    private ResultActions returnKg(String purchaseId, String kg) throws Exception {
        return asOwner(postWithKey("/api/v1/purchases/" + purchaseId + "/returns", """
                {"returnDate":"2026-10-05","returnedWeightKg":%s,"reason":"Moisture damage"}""".formatted(kg)));
    }

    private BigDecimal rawSilk() {
        return jdbc.queryForObject("select quantity_kg from inventory_balances where product_type = 'RAW_SILK'"
                + " and location = 'RAW_STOCK'", BigDecimal.class);
    }
}

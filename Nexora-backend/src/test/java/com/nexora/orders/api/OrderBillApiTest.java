package com.nexora.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.support.AbstractApiTest;

import tools.jackson.databind.JsonNode;

class OrderBillApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-DOC-06 E-ORD-12 generate bill for delivered order creates PDF document")
    void generateBill_forDeliveredOrder_createsPdfDocument() throws Exception {
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "100.000");
        String id = deliveredOrder("""
                [{"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00,"warpSpecification":"40/44 standard"}]""");

        JsonNode doc = body(asOwner(postJson("/api/v1/orders/" + id + "/bill", ""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.documentType").value("SALES_BILL"))
                .andExpect(jsonPath("$.referenceType").value("ORDER"))
                .andExpect(jsonPath("$.referenceId").value(id))
                .andExpect(jsonPath("$.fileName").value("bill-" + orderNumber(id).toLowerCase() + ".pdf")));

        String docId = doc.get("id").asString();

        // Download content and verify it is a valid PDF
        byte[] content = asOwner(get("/api/v1/documents/" + docId + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(content, 0, 8)).startsWith("%PDF-1.4");
        assertThat(new String(content)).contains("%%EOF");
        assertThat(new String(content)).contains("SMART SILK TRADERS");
    }

    @Test
    @DisplayName("FR-DOC-06 E-ORD-12 generate bill for non-delivered order returns 409")
    void generateBill_forNonDeliveredOrder_returns409() throws Exception {
        String id = createOrder(data.customer().id(), "SILK_WARP", "10.000", "900.00");
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());

        asOwner(postJson("/api/v1/orders/" + id + "/bill", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("FR-DOC-06 E-ORD-12 repeated bill generation supersedes previous version")
    void generateBill_repeated_supersedesPreviousVersion() throws Exception {
        data.openingStock(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK, "50.000");
        String id = deliveredOrder("""
                [{"productType":"KORA_WARP","requiredWeightKg":50.000,"ratePerKg":850.00}]""");

        JsonNode firstDoc = body(asOwner(postJson("/api/v1/orders/" + id + "/bill", ""))
                .andExpect(status().isCreated()));
        String firstDocId = firstDoc.get("id").asString();

        JsonNode secondDoc = body(asOwner(postJson("/api/v1/orders/" + id + "/bill", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supersedesDocumentId").value(firstDocId)));

        assertThat(secondDoc.get("id").asString()).isNotEqualTo(firstDocId);
    }

    @Test
    @DisplayName("FR-DOC-06 unknown order returns 404")
    void generateBill_unknownOrder_returns404() throws Exception {
        asOwner(postJson("/api/v1/orders/" + UUID.randomUUID() + "/bill", ""))
                .andExpect(status().isNotFound());
    }

    private String deliveredOrder(String items) throws Exception {
        String id = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","items":%s}""".formatted(data.customer().id(), today(), items)))
                .andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());
        asOwner(postJson("/api/v1/orders/" + id + "/ready", "")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"" + today() + "\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String orderNumber(String id) throws Exception {
        return body(asOwner(get("/api/v1/orders/" + id))).get("orderNumber").asString();
    }
}

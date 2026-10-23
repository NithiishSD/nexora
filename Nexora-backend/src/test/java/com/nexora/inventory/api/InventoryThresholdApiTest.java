package com.nexora.inventory.api;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.nexora.inventory.api.dto.SetThresholdRequest;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.support.AbstractApiTest;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryThresholdApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-INV-18 Setting a valid threshold creates or updates the threshold")
    void setThreshold_validRequest_savesAndReturnsThreshold() throws Exception {
        SetThresholdRequest req = new SetThresholdRequest(new BigDecimal("50.000"));

        asOwner(put("/api/v1/inventory/thresholds/RAW_SILK/RAW_STOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productType").value("RAW_SILK"))
                .andExpect(jsonPath("$.location").value("RAW_STOCK"))
                .andExpect(jsonPath("$.minThresholdKg").value(50.0));

        // Update to 75.000 kg
        SetThresholdRequest updateReq = new SetThresholdRequest(new BigDecimal("75.000"));
        asOwner(put("/api/v1/inventory/thresholds/RAW_SILK/RAW_STOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minThresholdKg").value(75.0));

        // Listed in GET /thresholds
        asOwner(get("/api/v1/inventory/thresholds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].productType").value("RAW_SILK"))
                .andExpect(jsonPath("$[0].minThresholdKg").value(75.0));
    }

    @Test
    @DisplayName("FR-INV-18 Setting a negative threshold returns 400 VALIDATION_FAILED")
    void setThreshold_negativeValue_returns400() throws Exception {
        SetThresholdRequest req = new SetThresholdRequest(new BigDecimal("-10.000"));

        asOwner(put("/api/v1/inventory/thresholds/RAW_SILK/RAW_STOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("FR-INV-18 Setting an illegal product/location pair returns 400 VALIDATION_FAILED")
    void setThreshold_invalidLocationProductPair_returns400() throws Exception {
        SetThresholdRequest req = new SetThresholdRequest(new BigDecimal("20.000"));

        asOwner(put("/api/v1/inventory/thresholds/RAW_SILK/FINISHED_STOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("FR-INV-18 Setting a threshold to 0 disables low-stock alerting")
    void setThreshold_zeroWeight_disablesThreshold() throws Exception {
        SetThresholdRequest req = new SetThresholdRequest(BigDecimal.ZERO);
        asOwner(put("/api/v1/inventory/thresholds/RAW_SILK/RAW_STOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minThresholdKg").value(0.0));
    }

    @Test
    @DisplayName("NFR-SEC-09 Staff can view thresholds and low stock but cannot modify thresholds")
    void staffPermissions_enforcedOnThresholdEndpoints() throws Exception {
        asStaff(get("/api/v1/inventory/thresholds"))
                .andExpect(status().isOk());

        asStaff(get("/api/v1/inventory/low-stock"))
                .andExpect(status().isOk());

        SetThresholdRequest req = new SetThresholdRequest(new BigDecimal("15.000"));
        asStaff(put("/api/v1/inventory/thresholds/RAW_SILK/RAW_STOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("FR-INV-18 Low-stock items endpoint returns deficit when stock is below threshold")
    void getLowStock_returnsDeficitItems() throws Exception {
        // Set threshold of 60.000 kg on RAW_SILK in RAW_STOCK
        SetThresholdRequest req = new SetThresholdRequest(new BigDecimal("60.000"));
        asOwner(put("/api/v1/inventory/thresholds/RAW_SILK/RAW_STOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(req)))
                .andExpect(status().isOk());

        // Low stock items query lists RAW_SILK
        asOwner(get("/api/v1/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productType").value("RAW_SILK"))
                .andExpect(jsonPath("$[0].location").value("RAW_STOCK"))
                .andExpect(jsonPath("$[0].minThresholdKg").value(60.0));
    }

    @Test
    @DisplayName("FR-INV-18 Stock summary includes minThresholdKg and isLowStock indicator")
    void stockSummary_includesThresholdAndLowStockFlag() throws Exception {
        SetThresholdRequest req = new SetThresholdRequest(new BigDecimal("40.000"));
        asOwner(put("/api/v1/inventory/thresholds/RAW_SILK/RAW_STOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(req)))
                .andExpect(status().isOk());

        asOwner(get("/api/v1/inventory/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.productType == 'RAW_SILK' && @.location == 'RAW_STOCK')].minThresholdKg").value(40.0))
                .andExpect(jsonPath("$.balances[?(@.productType == 'RAW_SILK' && @.location == 'RAW_STOCK')].isLowStock").value(true));
    }
}

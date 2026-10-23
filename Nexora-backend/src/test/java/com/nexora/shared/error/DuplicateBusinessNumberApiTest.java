package com.nexora.shared.error;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

class DuplicateBusinessNumberApiTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-DATA-06 reset counter reusing a number returns duplicate business number")
    void resetCounter_reusingANumber_returnsDuplicateBusinessNumber() throws Exception {
        String supplier = data.supplier().id().toString();
        String body = """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":10.000,"ratePerKg":100.00,
                 "paymentType":"CREDIT"}""".formatted(supplier, today());
        asOwner(postJson("/api/v1/purchases", body)).andExpect(status().isCreated());
        // the counter goes back (as after restoring an old backup of the sequence table only)
        jdbc.update("update business_number_sequences set next_value = 1 where prefix = 'PUR'");

        asOwner(postJson("/api/v1/purchases", body)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BUSINESS_NUMBER"));
    }
}

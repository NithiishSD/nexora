package com.nexora.settings.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.nexora.support.AbstractApiTest;

class SetupApiTest extends AbstractApiTest {

    @Test
    @DisplayName("setup status returns initialized true when owner exists")
    void setupStatus_returnsTrueWhenOwnerExists() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(true))
                .andExpect(jsonPath("$.businessName").isNotEmpty());
    }

    @Test
    @DisplayName("initialize returns 409 conflict when already initialized")
    void initialize_whenAlreadyInitialized_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "newowner",
                                  "password": "ValidPassword123#",
                                  "businessName": "New Silk Mill",
                                  "phone": "+919876543210",
                                  "city": "Salem"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }
}

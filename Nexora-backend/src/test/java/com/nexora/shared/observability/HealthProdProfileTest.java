package com.nexora.shared.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.nexora.support.AbstractIntegrationTest;

// The prod profile, configured exactly like OpenApiProdProfileTest so both share one cached application context.
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = "nexora.security.jwt-secret=prod-profile-test-secret-at-least-32-bytes")
class HealthProdProfileTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("NFR-OBS-03 prod profile hides health details")
    void prodProfile_hidesHealthDetails() throws Exception {
        // AC2: the status only, no components or database details
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(jsonPath("$.components").doesNotExist());
    }
}

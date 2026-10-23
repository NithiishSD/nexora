package com.nexora.shared.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nexora.support.AbstractApiTest;

class MetricsApiTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-OBS-04 prometheus endpoint served on management port only")
    void prometheusEndpoint_servedOnManagementPortOnly() throws Exception {
        // on the management port (8081, never published): HTTP latency, JVM and connection-pool metrics
        mockMvc.perform(get("/api/v1/parties"));
        mockMvc.perform(onPort(get("/actuator/prometheus"), 8081)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(Matchers.containsString("hikaricp_connections")))
                .andExpect(content().string(Matchers.containsString("http_server_requests_seconds")));
        // AC1: on the public port, no token -> 401; and even the owner is refused (403): it is never served there
        mockMvc.perform(onPort(get("/actuator/prometheus"), 8080)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        asOwner(onPort(get("/actuator/prometheus"), 8080)).andExpect(status().isForbidden());
        asOwner(onPort(get("/actuator/metrics"), 8080)).andExpect(status().isForbidden());
        // health stays public on the main port
        mockMvc.perform(onPort(get("/actuator/health"), 8080)).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder onPort(MockHttpServletRequestBuilder request, int port) {
        return request.with(r -> {
            r.setLocalPort(port);
            return r;
        });
    }
}

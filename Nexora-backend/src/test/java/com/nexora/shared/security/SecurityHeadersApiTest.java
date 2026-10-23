package com.nexora.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;

class SecurityHeadersApiTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-SEC-04 responses carry nosniff frame deny no store")
    void responses_carryNosniffFrameDenyNoStore() throws Exception {
        // AC1 on a success, a 401 and a 404 alike
        expectSecurityHeaders(asOwner(get("/api/v1/parties")).andExpect(status().isOk()));
        expectSecurityHeaders(mockMvc.perform(get("/api/v1/parties")).andExpect(status().isUnauthorized()));
        expectSecurityHeaders(asOwner(get("/api/v1/nothing-here")).andExpect(status().isNotFound()));
    }

    @Test
    @DisplayName("NFR-SEC-05 cors preflight is rejected")
    void corsPreflight_isRejected() throws Exception {
        // AC1: a preflight from another site gets no CORS permission
        mockMvc.perform(options("/api/v1/parties").header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        // and a normal call carrying an Origin header is answered without one either
        asOwner(get("/api/v1/parties").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private static void expectSecurityHeaders(ResultActions result) throws Exception {
        result.andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Permissions-Policy", Matchers.containsString("camera=()")));
    }
}

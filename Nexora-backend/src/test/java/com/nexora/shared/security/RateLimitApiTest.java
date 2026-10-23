package com.nexora.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

import jakarta.servlet.Filter;

// The shared limiter is switched off for the rest of the suite (one owner, hundreds of requests a minute), so these
// tests put a live limiter with the production limits in front of the real security chain and application.
class RateLimitApiTest extends AbstractApiTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    Filter securityChain;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    ProblemJsonWriter writer;

    private RateLimitFilter limiter;
    private MockMvc limited;

    @BeforeEach
    void liveLimiter() {
        limiter = new RateLimitFilter(true, 10, 300, jwtDecoder, writer, clock);
        limited = MockMvcBuilders.webAppContextSetup(context).addFilters(limiter, securityChain).build();
    }

    @Test
    @DisplayName("NFR-SEC-11 per user over300 requests per minute returns429 rate limited")
    void perUser_over300RequestsPerMinute_returns429RateLimited() throws Exception {
        String user = token();
        for (int i = 0; i < 300; i++) {
            limited.perform(get("/api/v1/parties?size=1").header(HttpHeaders.AUTHORIZATION, user))
                    .andExpect(status().isOk());
        }
        // AC1: the 301st request in the same minute
        limited.perform(get("/api/v1/parties?size=1").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // another user is not affected
        limited.perform(get("/api/v1/parties?size=1").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk());
        // the window slides: one minute after the oldest request a slot is free again
        assertThat(limiter.admit("k", 2, 0)).isZero();
        assertThat(limiter.admit("k", 2, 1_000)).isZero();
        assertThat(limiter.admit("k", 2, 30_000)).isEqualTo(30_000);
        assertThat(limiter.admit("k", 2, 60_000)).isZero();
    }

    @Test
    @DisplayName("NFR-SEC-11 auth endpoints over10 requests per minute per ip return429 with retry after")
    void authEndpoints_over10RequestsPerMinutePerIp_return429WithRetryAfter() throws Exception {
        for (int i = 0; i < 10; i++) {
            limited.perform(login("10.0.0.7")).andExpect(status().isUnauthorized());
        }
        String retryAfter = limited.perform(login("10.0.0.7")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 60);
        // the limit is per address: another one still reaches the login
        limited.perform(login("10.0.0.8")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("NFR-SEC-11 normal use below limit is never rejected")
    void normalUseBelowLimit_isNeverRejected() throws Exception {
        // AC2: a busy minute of normal use (a few logins, many screens) stays below both limits
        for (int i = 0; i < 9; i++) {
            limited.perform(login("10.0.0.9")).andExpect(status().isUnauthorized());
        }
        String user = token();
        for (int i = 0; i < 150; i++) {
            limited.perform(get("/api/v1/parties?size=1").header(HttpHeaders.AUTHORIZATION, user))
                    .andExpect(status().isOk());
        }
        limited.perform(post("/api/v1/auth/login").with(r -> {
            r.setRemoteAddr("10.0.0.9");
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    private String token() {
        return AuthTokens.bearer(tokens.mint(UUID.randomUUID(), List.of("PARTY_VIEW"), clock.instant(),
                Duration.ofMinutes(5)));
    }

    // A failed login from one address (an unknown user, so no account gets locked).
    private static MockHttpServletRequestBuilder login(String ip) {
        return post("/api/v1/auth/login").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"nobody\",\"password\":\"wrong-password-1\"}");
    }
}

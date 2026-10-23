package com.nexora.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;
import com.nexora.support.TestProbeController;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

// 401 versus 403 from the security filter chain, always as application/problem+json (09-SECURITY 2.5).
@Import(TestProbeController.class)
class SecurityErrorApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-AUTH-03 protected endpoint without token returns401 problem json")
    void protectedEndpoint_withoutToken_returns401ProblemJson() throws Exception {
        mockMvc.perform(get("/api/v1/parties"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").isString());
        // FR-AUTH-12 AC2: an unknown URL looks exactly the same to an unauthenticated caller
        mockMvc.perform(get("/api/v1/anything-unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("FR-AUTH-03 protected endpoint with expired token returns401 token expired")
    void protectedEndpoint_withExpiredToken_returns401TokenExpired() throws Exception {
        // AC2: issued 15 minutes and 1 second ago with a 15-minute lifetime = expired one second ago
        String expired = tokens.mint(UUID.randomUUID(), List.of("PARTY_VIEW"),
                Instant.now().minus(Duration.ofMinutes(15)).minusSeconds(1), Duration.ofMinutes(15));

        mockMvc.perform(get("/api/v1/parties").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("FR-AUTH-03 FR-AUTH-02 malformed or foreign signed token returns401 unauthenticated")
    void malformedOrForeignToken_returns401Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/parties").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // FR-AUTH-02 AC2: a well-formed token signed with a different secret
        NimbusJwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "some-other-secret-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        String forged = otherKey.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).subject(UUID.randomUUID().toString())
                        .issuedAt(now).expiresAt(now.plusSeconds(900))
                        .claim("permissions", List.of("PARTY_VIEW")).build())).getTokenValue();
        mockMvc.perform(get("/api/v1/parties").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("FR-AUTH-12 public endpoints without token are reachable")
    void publicEndpoints_withoutToken_areReachable() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());          // dev profile only
        // login/refresh/logout answer (here: 400 for an empty body) instead of 401
        for (String path : List.of("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    @DisplayName("FR-AUTH-09 staff user without permission returns403 access denied")
    void staffUser_withoutPermission_returns403AccessDenied() throws Exception {
        mockMvc.perform(get(TestProbeController.PAYMENT_ONLY)
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        // the owner holds the permission
        mockMvc.perform(get(TestProbeController.PAYMENT_ONLY)
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner())))
                .andExpect(status().isOk());
    }
}

package com.nexora.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

// created_by is filled from the JWT subject by JPA auditing; version starts at 0 (FR-AUTH-13).
class AuditingIntegrationTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-AUTH-13 NFR-OBS-06 created record stores creator from token and version")
    void createdRecord_storesCreatorFromTokenAndVersion() throws Exception {
        String body = mockMvc.perform(post("/api/v1/parties")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Kumar Silks\", \"roles\": [\"CUSTOMER\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID partyId = UUID.fromString(jsonMapper.readTree(body).get("id").asString());

        Map<String, Object> row = jdbc.queryForMap(
                "select created_by, created_at, version from parties where id = ?", partyId);
        UUID ownerId = jdbc.queryForObject("select id from users where username = 'owner'", UUID.class);

        assertThat(row.get("created_by")).isEqualTo(ownerId);
        assertThat(((Timestamp) row.get("created_at")).toInstant())
                .isBetween(Instant.now().minus(Duration.ofSeconds(5)), Instant.now());
        assertThat(row.get("version")).isEqualTo(0L);
    }

    @Test
    @DisplayName("FR-AUTH-13 request without token writes no row")
    void requestWithoutToken_writesNoRow() throws Exception {
        mockMvc.perform(post("/api/v1/parties").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Kumar Silks\", \"roles\": [\"CUSTOMER\"]}"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from parties", Integer.class)).isZero();
    }
}

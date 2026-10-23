package com.nexora.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;
import com.nexora.support.TestProbeController;

// The idempotency filter through a real @Idempotent route (the test probe creates a party per request).
@Import(TestProbeController.class)
class IdempotencyIntegrationTest extends AbstractApiTest {

    private static final String PATH = TestProbeController.IDEMPOTENT;

    @Test
    @DisplayName("FR-PAY-07 replay same key same body returns stored response")
    void replay_sameKeySameBody_returnsStoredResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = asOwner(postJson(PATH, "{\"name\":\"Ravi\"}").header(IdempotencyFilter.HEADER, key))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED))
                .andReturn().getResponse().getContentAsString();
        String location = jdbc.queryForObject("select response_location from idempotency_keys", String.class);

        // the retry gets the same status, body and Location, marked as replayed; the use case did not run again
        asOwner(postJson(PATH, "{\"name\":\"Ravi\"}").header(IdempotencyFilter.HEADER, key))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.REPLAYED, "true"))
                .andExpect(header().string(HttpHeaders.LOCATION, location))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(first));
        assertThat(jdbc.queryForObject("select count(*) from parties", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select expires_at - created_at from idempotency_keys", String.class))
                .isEqualTo("2 days");

        // a new key is a new request
        asOwner(postJson(PATH, "{\"name\":\"Ravi\"}").header(IdempotencyFilter.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from parties", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-PAY-07 same key different body returns409 duplicate request")
    void sameKeyDifferentBody_returns409DuplicateRequest() throws Exception {
        String key = UUID.randomUUID().toString();
        asOwner(postJson(PATH, "{\"name\":\"Ravi\"}").header(IdempotencyFilter.HEADER, key))
                .andExpect(status().isCreated());

        asOwner(postJson(PATH, "{\"name\":\"Kumar\"}").header(IdempotencyFilter.HEADER, key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
        assertThat(jdbc.queryForObject("select count(*) from parties", Integer.class)).isEqualTo(1);

        // a failed request stores no key (it rolls back with the use case): its retry, even with a corrected body, runs
        String failing = UUID.randomUUID().toString();
        asOwner(postJson(PATH, "{\"name\":\"fail\"}").header(IdempotencyFilter.HEADER, failing))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from parties", Integer.class)).isEqualTo(1);
        asOwner(postJson(PATH, "{\"name\":\"Kumar\"}").header(IdempotencyFilter.HEADER, failing))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED));
        assertThat(jdbc.queryForObject("select count(*) from idempotency_keys", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-PAY-07 required route without key returns400 validation failed")
    void requiredRoute_withoutKey_returns400ValidationFailed() throws Exception {
        asOwner(postJson(PATH, "{\"name\":\"Ravi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        asOwner(postJson(PATH, "{\"name\":\"Ravi\"}").header(IdempotencyFilter.HEADER, "short"))
                .andExpect(status().isBadRequest());
        asOwner(postJson(PATH, "{\"name\":\"Ravi\"}").header(IdempotencyFilter.HEADER, "bad key with spaces!"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from parties", Integer.class)).isZero();
        // permission first: STAFF without the permission gets 403, with or without a key, and nothing is stored
        mockMvc.perform(postJson(PATH, "{\"name\":\"Ravi\"}").header(HttpHeaders.AUTHORIZATION,
                        AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
        mockMvc.perform(postJson(PATH, "{\"name\":\"Ravi\"}").header(HttpHeaders.AUTHORIZATION,
                        AuthTokens.bearer(tokens.staff())).header(IdempotencyFilter.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from idempotency_keys", Integer.class)).isZero();
    }
}

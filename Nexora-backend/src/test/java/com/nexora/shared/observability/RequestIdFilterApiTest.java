package com.nexora.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

class RequestIdFilterApiTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-OBS-02 request id is echoed and generated when missing")
    void requestId_isEchoedAndGeneratedWhenMissing() throws Exception {
        try (LogCapture log = new LogCapture(RequestLoggingFilter.class)) {
            // AC1: the client's id comes back and is on the request's log line
            asOwner(get("/api/v1/parties").header("X-Request-Id", "abc-1"))
                    .andExpect(header().string("X-Request-Id", "abc-1"));
            assertThat(log.events()).anySatisfy(e -> assertThat(e.getMDCPropertyMap()).containsEntry("requestId", "abc-1"));
        }
        // no id, or one that is not a plain token (it could inject into logs): a fresh UUID instead
        String generated = mockMvc.perform(get("/api/v1/parties")).andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(generated)).isNotNull();
        String replaced = mockMvc.perform(get("/api/v1/parties").header("X-Request-Id", "evil\nline"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(replaced).isNotEqualTo("evil\nline");
        assertThat(UUID.fromString(replaced)).isNotNull();
    }
}

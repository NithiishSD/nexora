package com.nexora.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import ch.qos.logback.classic.spi.ILoggingEvent;

import com.nexora.support.AbstractApiTest;

class StructuredLogTest extends AbstractApiTest {

    @Value("${nexora.bootstrap.owner-username}")
    String ownerUsername;

    @Test
    @DisplayName("NFR-OBS-01 request log line has id method path status duration user")
    void requestLogLine_hasIdMethodPathStatusDurationUser() throws Exception {
        String owner = jdbc.queryForObject("select id::text from users where username = lower(?)", String.class,
                ownerUsername);
        try (LogCapture log = new LogCapture(RequestLoggingFilter.class)) {
            asOwner(get("/api/v1/parties").header("X-Request-Id", "obs-1"));
            mockMvc.perform(get("/api/v1/parties").header("X-Request-Id", "obs-2"));

            // AC1: one line per request with the six fields, as structured key-value pairs (JSON fields in prod)
            Map<String, Object> ok = fields(only(log, "obs-1"));
            assertThat(ok).containsEntry("requestId", "obs-1").containsEntry("method", "GET")
                    .containsEntry("path", "/api/v1/parties").containsEntry("status", 200)
                    .containsEntry("userId", owner).containsKey("durationMs");
            // a refused request is logged as well, without a user
            assertThat(fields(only(log, "obs-2"))).containsEntry("status", 401).containsEntry("userId", "-");
        }
        // production writes every line as JSON (Elastic Common Schema)
        YamlPropertiesFactoryBean prod = new YamlPropertiesFactoryBean();
        prod.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = prod.getObject();
        assertThat(properties.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }

    private static ILoggingEvent only(LogCapture log, String requestId) {
        return log.events().stream().filter(e -> requestId.equals(e.getMDCPropertyMap().get("requestId")))
                .reduce((a, b) -> {
                    throw new AssertionError("more than one line for " + requestId);
                }).orElseThrow();
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> fields = new HashMap<>(event.getMDCPropertyMap());
        event.getKeyValuePairs().forEach(kv -> fields.put(kv.key, kv.value));
        return fields;
    }
}

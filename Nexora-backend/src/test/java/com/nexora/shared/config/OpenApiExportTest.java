package com.nexora.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import com.nexora.support.AbstractApiTest;

// The API contract is frozen in openapi.json (repository, next to build.gradle): the build fails when the live spec
// differs, so no endpoint, field or status changes by accident. After an intended change, refresh the file with
// ./gradlew test --tests OpenApiExportTest -PupdateOpenapi and commit it with the change.
class OpenApiExportTest extends AbstractApiTest {

    private static final Path COMMITTED = Path.of("openapi.json");
    private static final JsonMapper PRETTY = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Test
    @DisplayName("exported spec matches committed openapi json")
    void exportedSpec_matchesCommittedOpenapiJson() throws Exception {
        String live = normalise(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        if (Boolean.getBoolean("nexora.openapi.update")) {
            Files.writeString(COMMITTED, live);
        }
        assertThat(Files.exists(COMMITTED)).as("openapi.json is committed (create it with -PupdateOpenapi)").isTrue();
        assertThat(live).as("the live API differs from openapi.json; refresh it with -PupdateOpenapi if intended")
                .isEqualTo(Files.readString(COMMITTED));
    }

    // Keys sorted at every level, the host-specific "servers" block dropped, two-space indentation, final newline.
    @SuppressWarnings("unchecked")
    private static String normalise(String json) {
        Map<String, Object> spec = new JsonMapper().readValue(json, Map.class);
        spec.remove("servers");
        return PRETTY.writeValueAsString(sorted(spec)) + "\n";
    }

    @SuppressWarnings("unchecked")
    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((k, v) -> result.put((String) k, sorted(v)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(v -> result.add(sorted(v)));
            return result;
        }
        return value;
    }
}

package com.nexora.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.nexora.NexoraApplication;
import com.nexora.support.AbstractIntegrationTest;

// NFR-OPS-02, NFR-SEC-02: every environment variable the configuration reads is documented in the example files, and
// the prod profile refuses to start when a required one is missing (no silent development default).
class ConfigurationFailFastTest extends AbstractIntegrationTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)(:?)");

    @Test
    @DisplayName("NFR-OPS-02 NFR-SEC-02 missing required variable in prod fails startup")
    void missingRequiredVariable_inProd_failsStartup() throws IOException {
        // AC1: every variable read by application.yml / application-prod.yml is documented, the prod ones without a
        // default in deploy/.env.prod.example
        Set<String> all = new TreeSet<>();
        Set<String> requiredInProd = new TreeSet<>();
        for (String file : new String[] {"application.yml", "application-prod.yml"}) {
            Matcher m = PLACEHOLDER.matcher(Files.readString(Path.of("src/main/resources", file)));
            while (m.find()) {
                all.add(m.group(1));
                if (m.group(2).isEmpty()) {
                    requiredInProd.add(m.group(1));
                }
            }
        }
        String dev = Files.readString(Path.of(".env.example"));
        String prod = Files.readString(Path.of("../deploy/.env.prod.example"));
        assertThat(all).allSatisfy(name -> assertThat(dev + prod).as(name).contains(name));
        assertThat(requiredInProd).containsExactlyInAnyOrderElementsOf(RequiredConfigurationCheck.REQUIRED_IN_PROD.values());
        assertThat(requiredInProd).allSatisfy(name ->
                assertThat(prod).as(name).containsPattern("(?m)^" + name + "="));

        // AC2: the prod profile with one required variable missing does not start, and says which one
        for (String missing : requiredInProd) {
            Map<String, Object> env = prodEnvironment();
            env.remove(missing);
            // either our check's sentence, or (for a variable Spring itself resolves earlier, such as DB_URL inside
            // FLYWAY_URL's default) Spring's own "Could not resolve placeholder 'DB_URL'": both name the variable
            assertThatThrownBy(() -> start(env)).as(missing).satisfiesAnyOf(
                    e -> assertThat(e).hasStackTraceContaining("Missing required configuration for the prod profile: "
                            + missing),
                    e -> assertThat(e).hasStackTraceContaining("Could not resolve placeholder '" + missing + "'"));
        }
        // and a JWT secret shorter than 32 bytes is refused as well
        Map<String, Object> shortSecret = prodEnvironment();
        shortSecret.put("JWT_SECRET", "too-short-secret");
        assertThatThrownBy(() -> start(shortSecret)).hasStackTraceContaining("at least 32 bytes");
        // with everything present it starts (the control case)
        start(prodEnvironment()).close();
    }

    private static Map<String, Object> prodEnvironment() {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("DB_URL", POSTGRES.getJdbcUrl());
        env.put("DB_USERNAME", POSTGRES.getUsername());
        env.put("DB_PASSWORD", POSTGRES.getPassword());
        env.put("FLYWAY_USER", POSTGRES.getUsername());
        env.put("FLYWAY_PASSWORD", POSTGRES.getPassword());
        env.put("JWT_SECRET", "fail-fast-test-secret-at-least-32-bytes-long");
        return env;
    }

    private static org.springframework.context.ConfigurableApplicationContext start(Map<String, Object> env) {
        // the variables as default properties (like the environment); the ports as arguments, which win over the yml
        return new SpringApplicationBuilder(NexoraApplication.class).profiles("prod").properties(env)
                .run("--server.port=0", "--nexora.management.port=0", "--spring.main.banner-mode=off");
    }
}

package com.nexora.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

// Starts a tiny Spring context that only binds nexora.security.* and checks the fail-fast rule.
class SecurityPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    @DisplayName("NFR-SEC-02 short jwt secret fails startup")
    void shortJwtSecret_failsStartup() {
        runner.withPropertyValues("nexora.security.jwt-secret=only-31-bytes-long-secret-value")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("at least 32 bytes"));
    }

    @Test
    @DisplayName("NFR-SEC-02 missing jwt secret fails startup")
    void missingJwtSecret_failsStartup() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("NFR-SEC-02 secret of32 bytes starts with default lifetimes")
    void secretOf32Bytes_startsWithDefaults() {
        runner.withPropertyValues("nexora.security.jwt-secret=exactly-32-bytes-long-secret-xyz")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SecurityProperties properties = context.getBean(SecurityProperties.class);
                    assertThat(properties.accessTokenTtl()).hasMinutes(15);
                    assertThat(properties.refreshTokenTtl()).hasDays(7);
                    assertThat(properties.maxFailedLogins()).isEqualTo(5);
                    assertThat(properties.lockoutDuration()).hasMinutes(15);
                });
    }

    // Binds only SecurityProperties: no database, no web server.
    @Configuration
    @EnableConfigurationProperties(SecurityProperties.class)
    static class PropertiesOnly {
    }
}

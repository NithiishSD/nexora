package com.nexora.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Binds the "nexora.security.*" settings from application.yml / environment variables.
// A record gives an immutable settings object; the compact constructor validates at startup
// (fail fast: the application refuses to start with a weak or missing secret).
@ConfigurationProperties(prefix = "nexora.security")
public record SecurityProperties(
        String jwtSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockoutDuration) {

    public SecurityProperties {
        // HS256 needs a key of at least 256 bits = 32 bytes.
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "nexora.security.jwt-secret (env JWT_SECRET) must be at least 32 bytes long");
        }
    }
}

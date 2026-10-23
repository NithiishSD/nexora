package com.nexora.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nexora.shared.security.Permission;
import com.nexora.shared.security.SecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

// Unit test with a fixed clock: the token issued at 10:00:00 must expire at 10:15:00 (FR-AUTH-02 AC1).
class AccessTokenServiceTest {

    private static final Instant TEN_O_CLOCK = Instant.parse("2026-10-07T10:00:00Z");
    private final SecretKey key = new SecretKeySpec(
            "unit-test-secret-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final SecurityProperties properties = new SecurityProperties(
            "unit-test-secret-at-least-32-bytes-long", Duration.ofMinutes(15), Duration.ofDays(7), 5, Duration.ofMinutes(15));
    private final AccessTokenService service = new AccessTokenService(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties, Clock.fixed(TEN_O_CLOCK, ZoneOffset.UTC));

    @Test
    @DisplayName("FR-AUTH-02 issue contains issuer subject permissions and15 minute expiry")
    void issue_containsIssuerSubjectPermissionsAnd15MinuteExpiry() {
        UUID userId = UUID.randomUUID();

        String token = service.issue(userId, "owner", List.of("OWNER"),
                List.of(Permission.PARTY_VIEW, Permission.PAYMENT_CREATE));

        Jwt jwt = decoder().decode(token);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("nexora");
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getIssuedAt()).isEqualTo(TEN_O_CLOCK);
        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse("2026-10-07T10:15:00Z"));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsString("username")).isEqualTo("owner");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("OWNER");
        assertThat(jwt.getClaimAsStringList("permissions")).containsExactlyInAnyOrder("PARTY_VIEW", "PAYMENT_CREATE");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(service.accessTokenSeconds()).isEqualTo(900);
    }

    // Checks the signature and issuer only (the fixed issue time is in the past of the real clock).
    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new JwtIssuerValidator("nexora"));
        return decoder;
    }
}

package com.nexora.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

// The access token is a JWT signed with HS256 (one shared secret from the environment).
// Spring's own Nimbus support is used, so no extra JWT library is needed (ADR-014).
// (javax.crypto is part of the JDK itself, not the old Java EE javax.* packages, so it is correct here.)
@Configuration
public class JwtConfig {

    // Value of the "iss" claim. Tokens with another issuer are rejected.
    public static final String ISSUER = "nexora";

    // The HMAC key built from the configured secret (at least 32 bytes, checked by SecurityProperties).
    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    // Signs new tokens (used by the login and refresh use cases).
    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    // Verifies the signature, the expiry and the issuer of every incoming token.
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)   // accept only HS256, never "none" or another algorithm
                .build();
        // No clock-skew allowance: the same server (same clock) signs and checks the token, so a token
        // is rejected the moment its "exp" has passed (FR-AUTH-03: expired one second ago -> 401).
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);                 // the application's one time source
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                timestamps, new JwtIssuerValidator(ISSUER));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}

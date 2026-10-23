package com.nexora.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.nexora.shared.security.JwtConfig;
import com.nexora.shared.security.Permission;
import com.nexora.shared.security.SecurityProperties;

// Builds and signs the short-lived access token (JWT, HS256, 15 minutes; 09-SECURITY 2.2).
@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    // Only identity and authorization data go into the token: never passwords, balances or party data.
    public String issue(UUID userId, String username, Collection<String> roles, Collection<Permission> permissions) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)                               // checked on every request
                .subject(userId.toString())                             // who: becomes created_by on records
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))       // 15 minutes later
                .id(UUID.randomUUID().toString())                       // "jti": makes every token unique
                .claim("username", username)
                .claim("roles", roles.stream().sorted().toList())
                // the permissions claim becomes the Spring authorities checked by @PreAuthorize
                .claim("permissions", permissions.stream().map(Enum::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // Lifetime in seconds, returned to the client as "expiresIn".
    public long accessTokenSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}

package com.nexora.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.nexora.identity.domain.User;
import com.nexora.identity.infrastructure.RoleRepository;
import com.nexora.identity.infrastructure.UserRepository;
import com.nexora.shared.security.JwtConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Gives API tests real tokens: by logging in through the real endpoint, or by signing one directly with
// the application's own JwtEncoder (for expired or hand-made tokens).
public class AuthTokens {

    // Password used for every user a test creates (meets the password policy).
    public static final String TEST_PASSWORD = "Test-Password-2026";

    // Cached owner token (shared by all API tests of one JVM); renewed well before its 15 minutes run out.
    private static String ownerToken;
    private static Instant ownerTokenIssuedAt;
    private static String staffUsername;

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;
    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String ownerUsername;
    private final String ownerPassword;

    public AuthTokens(MockMvc mockMvc, JsonMapper jsonMapper, JwtEncoder jwtEncoder, UserRepository userRepository,
                      RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                      String ownerUsername, String ownerPassword) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.ownerUsername = ownerUsername;
        this.ownerPassword = ownerPassword;
    }

    // Access token of the owner user (all permissions), obtained through POST /auth/login.
    public synchronized String owner() {
        if (ownerToken == null || ownerTokenIssuedAt.isBefore(Instant.now().minus(Duration.ofMinutes(10)))) {
            ownerToken = login(ownerUsername, ownerPassword).get("accessToken").asString();
            ownerTokenIssuedAt = Instant.now();
        }
        return ownerToken;
    }

    // Access token of a STAFF user (read-only permissions), for 403 tests.
    public synchronized String staff() {
        if (staffUsername == null) {
            staffUsername = createUser("STAFF");
        }
        return login(staffUsername, TEST_PASSWORD).get("accessToken").asString();
    }

    // Creates an active user with the given role and TEST_PASSWORD; returns the (unique) username.
    public String createUser(String roleName) {
        String username = roleName.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(User.create(username, passwordEncoder.encode(TEST_PASSWORD), "Test " + roleName,
                roleRepository.findByName(roleName).orElseThrow()));
        return username;
    }

    // Logs in through the real endpoint and returns the JSON body (accessToken, refreshToken, ...).
    public JsonNode login(String username, String password) {
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(Map.of("username", username, "password", password))))
                    .andReturn();
            if (result.getResponse().getStatus() != 200) {
                throw new IllegalStateException("login failed for " + username + ": " + result.getResponse().getStatus());
            }
            return jsonMapper.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Any token we want: subject, permissions, issue time and lifetime. A past issue time plus a short
    // lifetime gives an already expired token.
    public String mint(UUID userId, Collection<String> permissions, Instant issuedAt, Duration ttl) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .claim("permissions", List.copyOf(permissions))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // Value for the Authorization header.
    public static String bearer(String token) {
        return "Bearer " + token;
    }
}

package com.nexora.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Creates opaque refresh tokens and the hash that is stored instead of them (09-SECURITY 2.3).
// Only the hash goes into the database, so a leaked database copy contains no usable token.
public final class RefreshTokenHasher {

    // SecureRandom = cryptographically strong randomness (java.util.Random is predictable).
    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenHasher() {
    }

    // 32 random bytes as Base64-URL without padding: a 43-character string, safe in JSON and URLs.
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 of the token as 64 lower-case hex characters (the refresh_tokens.token_hash column).
    // A fast hash is fine here: the token is 256 random bits, so it cannot be guessed (unlike a password).
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // every Java runtime must provide SHA-256, so this cannot happen
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

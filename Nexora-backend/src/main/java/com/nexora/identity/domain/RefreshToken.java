package com.nexora.identity.domain;

import java.time.Instant;
import java.util.UUID;

import com.nexora.shared.domain.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// One issued refresh token. Only the SHA-256 hash of the token is stored: a stolen database copy
// cannot be turned back into usable tokens (ADR-014).
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AuditableEntity {

    // Same module as User, but kept as a plain id: the token never needs the user graph loaded.
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;                      // null while the token may still be used

    protected RefreshToken() {
        // for JPA only
    }

    public static RefreshToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    // Revoking twice keeps the first time (logout is idempotent).
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}

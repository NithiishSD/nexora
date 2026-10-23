package com.nexora.identity.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// GET /api/v1/auth/me: who is logged in and what they may do. Never a hash or a token.
public record MeResponse(
        UUID id,
        String username,
        String displayName,
        List<String> roles,
        List<String> permissions,
        Instant lastLoginAt) {
}

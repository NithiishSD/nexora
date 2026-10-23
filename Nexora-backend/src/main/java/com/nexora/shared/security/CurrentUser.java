package com.nexora.shared.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

// Small helper for use cases that need the id of the logged-in user.
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        throw new IllegalStateException("No authenticated user in the current security context");
    }

    // The logged-in user, or null outside a request (scheduled jobs) or before login (lockout, token reuse).
    public static UUID idOrNull() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                ? UUID.fromString(jwt.getToken().getSubject()) : null;
    }
}

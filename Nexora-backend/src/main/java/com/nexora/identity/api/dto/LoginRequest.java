package com.nexora.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/auth/login.
public record LoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 200) String password) {

    // A record's generated toString prints every field. Overridden so the password can never end up
    // in a log line by accident (NFR-SEC-06).
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}

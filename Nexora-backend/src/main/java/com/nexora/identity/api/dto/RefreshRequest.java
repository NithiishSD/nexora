package com.nexora.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/auth/refresh and POST /api/v1/auth/logout.
public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {

    // The token is a secret: never print it.
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}

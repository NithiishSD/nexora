package com.nexora.identity.api.dto;

// Returned by login and refresh. The refresh token is shown to the client once; only its hash is stored.
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,      // always "Bearer"
        long expiresIn) {      // access-token lifetime in seconds (900)

    // Tokens are secrets: keep them out of any accidental log line.
    @Override
    public String toString() {
        return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}

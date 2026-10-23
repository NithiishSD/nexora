package com.nexora.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    @DisplayName("NFR-SEC-11 rate limited maps to429")
    void rateLimited_mapsTo429() {
        assertThat(ErrorCode.RATE_LIMITED.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("NFR-USE-04 codes added by the plan map to their statuses")
    void planCodes_mapToTheirStatuses() {
        assertThat(ErrorCode.METHOD_NOT_ALLOWED.status()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ErrorCode.DUPLICATE_RESOURCE.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVALID_PARTY_ROLE.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }
}

package com.nexora.shared.idempotency;

// What a finished idempotent request answered: replayed byte for byte to a retry with the same key and body.
record StoredResponse(int status, String body, String location) {
}

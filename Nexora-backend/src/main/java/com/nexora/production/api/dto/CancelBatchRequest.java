package com.nexora.production.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/production-batches/{id}/cancel (E-PROD-07): why the batch is cancelled (kept in the audit log).
public record CancelBatchRequest(@NotBlank @Size(max = 255) String reason) {
}

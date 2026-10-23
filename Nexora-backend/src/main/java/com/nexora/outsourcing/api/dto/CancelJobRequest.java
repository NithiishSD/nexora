package com.nexora.outsourcing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/outsourcing-jobs/{id}/cancel (E-OUT-06): why the job is cancelled (kept in the audit log).
public record CancelJobRequest(@NotBlank @Size(max = 255) String reason) {
}

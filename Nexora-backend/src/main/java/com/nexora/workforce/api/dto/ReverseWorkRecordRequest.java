package com.nexora.workforce.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/work-records/{id}/reverse (E-WORK-04): why the record is wrong (kept in the audit log).
public record ReverseWorkRecordRequest(@NotBlank @Size(max = 255) String reason) {
}

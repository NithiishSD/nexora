package com.nexora.production.api.dto;

import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

// Optional body of POST /api/v1/production-batches/{id}/start (E-PROD-05): the movement date, default today.
public record StartBatchRequest(@BusinessDate LocalDate startDate) {
}

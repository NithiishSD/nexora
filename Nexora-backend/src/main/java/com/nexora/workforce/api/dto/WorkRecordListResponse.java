package com.nexora.workforce.api.dto;

import java.math.BigDecimal;

import com.nexora.shared.api.PageResponse;

// GET /api/v1/work-records (E-WORK-02): one page plus the amount of every matching recorded (not reversed) row.
public record WorkRecordListResponse(PageResponse<WorkRecordResponse> page, BigDecimal totalAmount) {
}

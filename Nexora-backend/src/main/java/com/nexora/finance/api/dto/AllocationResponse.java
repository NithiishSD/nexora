package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.AllocationTargetType;

// One stored allocation; released = the payment was reversed (the row itself never changes).
public record AllocationResponse(UUID id, AllocationTargetType targetType, UUID targetId, String targetLabel,
                                 LocalDate obligationDate, BigDecimal amount, boolean released) {
}

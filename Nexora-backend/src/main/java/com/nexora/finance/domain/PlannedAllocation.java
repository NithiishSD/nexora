package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.util.UUID;

// One allocation the planner decided on, before it is stored.
public record PlannedAllocation(AllocationTargetType targetType, UUID targetId, BigDecimal amount) {
}

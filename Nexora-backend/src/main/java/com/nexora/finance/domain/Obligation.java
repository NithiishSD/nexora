package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Something a party still owes or is owed, in the order it is settled: obligation date, then business number, then
// line (FR-PAY-02). outstanding = amount - allocations of active payments, always > 0 here.
public record Obligation(AllocationTargetType targetType, UUID targetId, LocalDate obligationDate, String label,
                         BigDecimal outstanding) {
}

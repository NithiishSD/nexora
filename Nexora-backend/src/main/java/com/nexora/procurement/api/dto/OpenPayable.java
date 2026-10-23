package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// One supplier obligation still to be paid: a purchase (purchaseId set) or an opening payable (purchaseId null).
// dueDate can be null for an opening payable entered without one.
public record OpenPayable(UUID supplierId, UUID purchaseId, String reference, LocalDate date, LocalDate dueDate,
                          BigDecimal outstanding) {
}

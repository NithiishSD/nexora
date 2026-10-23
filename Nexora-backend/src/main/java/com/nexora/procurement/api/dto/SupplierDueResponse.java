package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.parties.application.PartyRef;

// One purchase still to be paid that falls due soon or is already overdue (FR-NTF-06), computed on read.
public record SupplierDueResponse(UUID purchaseId, String purchaseNumber, PartyRef supplier, LocalDate dueDate,
                                  BigDecimal outstanding, boolean overdue, int overdueDays) {
}

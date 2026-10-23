package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.parties.application.PartyRef;

// GET /api/v1/payables/suppliers (E-PAY-08): every supplier with something to pay.
public record SupplierPayablesOverviewResponse(BigDecimal totalPayable, BigDecimal totalOverdue, BigDecimal dueWithin7Days,
                                               List<SupplierLine> suppliers) {

    public record SupplierLine(PartyRef supplier, BigDecimal outstanding, BigDecimal overdue, LocalDate oldestDueDate) {
    }
}

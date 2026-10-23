package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyRef;
import com.nexora.procurement.domain.PurchaseStatus;
import com.nexora.shared.domain.PaymentStatus;

// One row of the purchase list (E-PROC-02).
public record PurchaseSummaryResponse(
        UUID id,
        String purchaseNumber,
        PartyRef supplier,
        ProductType productType,
        LocalDate purchaseDate,
        BigDecimal orderedWeightKg,
        BigDecimal receivedWeightKg,
        BigDecimal ratePerKg,
        BigDecimal orderedValue,
        PurchaseStatus status,
        PaymentStatus paymentStatus,
        LocalDate dueDate,
        BigDecimal outstandingAmount,
        boolean overdue) {
}

package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyRef;
import com.nexora.procurement.domain.PaymentType;
import com.nexora.procurement.domain.PurchaseStatus;
import com.nexora.shared.domain.PaymentStatus;

// Full view of one purchase (05-API-SPEC section 6). Every weight and amount below the terms is derived by the
// backend from the receipts and returns; none is stored or accepted from the client.
public record PurchaseResponse(
        UUID id,
        String purchaseNumber,
        PartyRef supplier,
        ProductType productType,
        LocalDate purchaseDate,
        BigDecimal orderedWeightKg,
        BigDecimal ratePerKg,            // historical, never changes
        BigDecimal orderedValue,         // round(ordered x rate, 2)
        PaymentType paymentType,
        int creditDays,
        LocalDate dueDate,
        String billReference,
        PurchaseStatus status,
        BigDecimal receivedWeightKg,
        BigDecimal acceptedWeightKg,
        BigDecimal rejectedWeightKg,
        BigDecimal returnedWeightKg,
        BigDecimal remainingWeightKg,    // ordered - received (0 when cancelled)
        BigDecimal payableAmount,        // round((accepted - returned) x rate, 2), assumption Q1
        BigDecimal allocatedAmount,      // allocations of active payments
        BigDecimal outstandingAmount,    // payable - allocated (negative = credit with the supplier)
        PaymentStatus paymentStatus,
        boolean overdue,                 // outstanding > 0 and due date passed, never when cancelled
        int overdueDays,
        List<ReceiptResponse> receipts,  // oldest first
        List<SupplierReturnResponse> returns,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {
}

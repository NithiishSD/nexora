package com.nexora.outsourcing.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;
import com.nexora.outsourcing.domain.JobStatus;
import com.nexora.parties.application.PartyRef;
import com.nexora.shared.domain.PaymentStatus;

// One outsourcing job (05-API-SPEC section 14). Weights outside, payable and payment fields are derived.
public record JobResponse(
        UUID id,
        String jobNumber,
        PartyRef manufacturer,
        ProductType productType,
        BigDecimal issuedWeightKg,
        BigDecimal receivedWeightKg,
        BigDecimal wastageWeightKg,
        BigDecimal outsideWeightKg,            // issued - received - wastage once issued, 0 before
        BigDecimal ratePerKg,                  // historical
        BigDecimal payableAmount,              // received x rate
        BigDecimal allocatedAmount,
        BigDecimal outstandingAmount,
        PaymentStatus paymentStatus,
        LocalDate issueDate,
        LocalDate receivedDate,
        JobStatus status,
        List<Link> orderItems,
        List<Receipt> receipts,                // detail only (null in lists), from the movement ledger
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {

    public record Receipt(LocalDate receiptDate, BigDecimal receivedWeightKg, BigDecimal wastageWeightKg,
                          String receiptMovementNumber, String wastageMovementNumber) {
    }

    public record Link(UUID orderItemId, UUID orderId, String orderNumber, int lineNo, BigDecimal allocatedWeightKg) {
    }
}

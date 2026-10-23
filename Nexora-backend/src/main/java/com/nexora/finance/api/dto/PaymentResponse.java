package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.finance.domain.PaymentDirection;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.finance.domain.RecordStatus;
import com.nexora.parties.application.PartyRef;

// One payment with its split (05-API-SPEC section 9). unallocatedAmount of a customer payment is the advance.
public record PaymentResponse(
        UUID id,
        String paymentNumber,
        PaymentPurpose purpose,
        PaymentDirection direction,
        PartyRef party,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        BigDecimal unallocatedAmount,
        PaymentMethod paymentMethod,
        AccountRef account,
        LocalDate paymentDate,
        String referenceNumber,
        RecordStatus status,
        Instant reversedAt,
        String reversalReason,
        UUID financialTransactionId,
        UUID reversalTransactionId,
        List<AllocationResponse> allocations,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID refundOfPaymentId) {

    public PaymentResponse(UUID id, String paymentNumber, PaymentPurpose purpose, PaymentDirection direction,
                           PartyRef party, BigDecimal amount, BigDecimal allocatedAmount, BigDecimal unallocatedAmount,
                           PaymentMethod paymentMethod, AccountRef account, LocalDate paymentDate, String referenceNumber,
                           RecordStatus status, Instant reversedAt, String reversalReason, UUID financialTransactionId,
                           UUID reversalTransactionId, List<AllocationResponse> allocations, String notes,
                           Instant createdAt, Instant updatedAt, UUID createdBy) {
        this(id, paymentNumber, purpose, direction, party, amount, allocatedAmount, unallocatedAmount, paymentMethod,
                account, paymentDate, referenceNumber, status, reversedAt, reversalReason, financialTransactionId,
                reversalTransactionId, allocations, notes, createdAt, updatedAt, createdBy, null);
    }
}

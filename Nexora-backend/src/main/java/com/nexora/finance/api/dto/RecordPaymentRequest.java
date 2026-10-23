package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/payments (E-PAY-01). The direction follows from the purpose (a "direction" sent by a client is
// ignored: refunds are post-MVP). No paymentDate = today (NFR-USE-01). No allocations = oldest first.
public record RecordPaymentRequest(
        @NotNull PaymentPurpose purpose,
        @NotNull UUID partyId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull PaymentMethod paymentMethod,
        @NotNull UUID financialAccountId,
        @BusinessDate LocalDate paymentDate,
        @Size(max = 100) String referenceNumber,
        @Size(max = 1000) String notes,
        @Size(max = 200) List<@Valid @NotNull AllocationRequest> allocations,
        Boolean pendingCheque) {

    public RecordPaymentRequest(PaymentPurpose purpose, UUID partyId, BigDecimal amount, PaymentMethod paymentMethod,
                                UUID financialAccountId, LocalDate paymentDate, String referenceNumber, String notes,
                                List<AllocationRequest> allocations) {
        this(purpose, partyId, amount, paymentMethod, financialAccountId, paymentDate, referenceNumber, notes, allocations, false);
    }
}

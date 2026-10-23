package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.PaymentDirection;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.finance.domain.RecordStatus;
import com.nexora.parties.application.PartyRef;

// One row of GET /api/v1/payments (E-PAY-02).
public record PaymentSummaryResponse(UUID id, String paymentNumber, PaymentPurpose purpose, PaymentDirection direction,
                                     PartyRef party, BigDecimal amount, BigDecimal unallocatedAmount,
                                     PaymentMethod paymentMethod, AccountRef account, LocalDate paymentDate,
                                     RecordStatus status) {
}

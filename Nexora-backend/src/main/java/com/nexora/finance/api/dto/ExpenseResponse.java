package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.ExpenseCategory;
import com.nexora.finance.domain.ExpenseKind;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.RecordStatus;
import com.nexora.finance.domain.Recurrence;

// One expense (E-FIN-12) with the id of its OUT ledger row.
public record ExpenseResponse(
        UUID id,
        String expenseNumber,
        String category,
        ExpenseKind expenseKind,
        Recurrence recurrence,
        BigDecimal amount,
        LocalDate expenseDate,
        AccountRef account,
        PaymentMethod paymentMethod,
        String description,
        String documentReference,
        RecordStatus status,
        Instant reversedAt,
        String reversalReason,
        UUID financialTransactionId,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {
}

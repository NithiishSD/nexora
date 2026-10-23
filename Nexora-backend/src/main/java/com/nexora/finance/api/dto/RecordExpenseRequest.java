package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.ExpenseCategory;
import com.nexora.finance.domain.ExpenseKind;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.Recurrence;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/expenses (E-FIN-12). expenseKind defaults to BUSINESS, recurrence to ONE_TIME.
public record RecordExpenseRequest(
        @NotBlank String category,
        ExpenseKind expenseKind,
        Recurrence recurrence,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull @BusinessDate LocalDate expenseDate,          // the actual payment date
        @NotNull UUID financialAccountId,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 1000) String description,
        @Size(max = 100) String documentReference) {
}

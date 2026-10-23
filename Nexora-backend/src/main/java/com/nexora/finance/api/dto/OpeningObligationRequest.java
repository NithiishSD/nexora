package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/opening-obligations (E-PAY-11): CUSTOMER = a receivable, every other purpose = a payable.
public record OpeningObligationRequest(
        @NotNull PaymentPurpose purpose,
        @NotNull UUID partyId,
        @NotNull @BusinessDate LocalDate obligationDate,         // on or before today
        @BusinessDate LocalDate dueDate,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal originalAmount,
        @NotBlank @Size(max = 255) String referenceNote) {       // notebook page or old bill
}

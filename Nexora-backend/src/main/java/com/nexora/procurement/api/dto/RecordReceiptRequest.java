package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/purchases/{id}/receipts (E-PROC-06): what physically arrived. accepted + rejected may
// not exceed received (checked by the purchase, 400 on acceptedWeightKg); only accepted kg enter stock.
public record RecordReceiptRequest(
        @NotNull @BusinessDate LocalDate receiptDate,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal receivedWeightKg,
        @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal acceptedWeightKg,
        @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal rejectedWeightKg,
        @Size(max = 1000) String notes) {
}

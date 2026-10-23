package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.finance.domain.AccountType;
import com.nexora.shared.api.BusinessDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/finance/accounts (E-FIN-01). Only the last four digits of a bank account number are accepted
// (a longer value is 400): the full number is never sent, stored or logged (NFR-SEC-06).
public record CreateAccountRequest(
        @NotBlank @Size(max = 100) String accountName,
        @NotNull AccountType accountType,
        @Size(max = 100) String bankName,                       // BANK: required; CASH: must be absent
        @Pattern(regexp = "^[0-9]{4}$") String accountNumberLast4,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal openingBalance,
        @BusinessDate LocalDate openingDate) {                  // required when openingBalance > 0
}

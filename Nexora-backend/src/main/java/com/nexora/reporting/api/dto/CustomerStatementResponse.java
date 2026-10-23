package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.parties.application.PartyRef;

// GET /api/v1/customers/{id}/statement (E-RPT-03). Balance = what the customer owes, net of money held as advance:
// closingBalance at today equals outstanding - advance.
public record CustomerStatementResponse(PartyRef customer, LocalDate from, LocalDate to, BigDecimal openingBalance,
                                        List<Line> lines, BigDecimal closingBalance) {

    // kind: ORDER, PAYMENT, REVERSAL, OPENING_OBLIGATION
    public record Line(LocalDate date, String kind, String number, BigDecimal debit, BigDecimal credit,
                       BigDecimal runningBalance) {
    }
}

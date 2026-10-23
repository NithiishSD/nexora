package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.nexora.chits.domain.ChitStatus;
import com.nexora.loans.domain.LoanStatus;
import com.nexora.parties.application.PartyRef;

// GET /api/v1/reports/loans-and-chits (E-RPT-14). Loans are liabilities and chits investments: neither is revenue
// or an operating expense (only loan interest is, in the expense report; FR-CHIT-07).
public record LoansAndChitsReport(List<LoanLine> loans, List<ChitLine> chits) {

    public record LoanLine(String loanNumber, PartyRef lender, BigDecimal principal, BigDecimal outstandingPrincipal,
                           BigDecimal interestPaid, LoanStatus status) {
    }

    // payoutReceived is null until a payout counts.
    public record ChitLine(String chitNumber, String chitName, BigDecimal totalContributed,
                           int remainingExpectedContributions, BigDecimal organiserPayments, BigDecimal payoutReceived,
                           ChitStatus status) {
    }
}

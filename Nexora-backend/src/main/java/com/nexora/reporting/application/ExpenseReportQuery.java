package com.nexora.reporting.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.ExpenseSummaryResponse;
import com.nexora.finance.application.ExpenseService;
import com.nexora.finance.application.LedgerQueryService;
import com.nexora.finance.domain.TransactionType;
import com.nexora.reporting.api.dto.ExpenseReport;

// FR-RPT-08: the expense summary (E-FIN-15) plus loan interest, which is an expense recorded on the loan, not in
// the expenses table (FR-LOAN-08).
@Service
public class ExpenseReportQuery {

    private final ExpenseService expenses;
    private final LedgerQueryService ledger;

    public ExpenseReportQuery(ExpenseService expenses, LedgerQueryService ledger) {
        this.expenses = expenses;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public ExpenseReport report(LocalDate from, LocalDate to) {
        ExpenseSummaryResponse summary = expenses.summary(from, to);                 // 400 when from is after to
        return new ExpenseReport(from, to, summary.businessTotal(), summary.personalDrawingsTotal(),
                ledger.unreversedTotal(TransactionType.LOAN_INTEREST_PAYMENT, from, to), summary.byCategory(),
                summary.byMonth());
    }
}

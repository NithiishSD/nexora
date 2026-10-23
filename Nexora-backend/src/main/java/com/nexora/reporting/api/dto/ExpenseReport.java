package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.finance.api.dto.ExpenseSummaryResponse.CategoryTotal;
import com.nexora.finance.api.dto.ExpenseSummaryResponse.MonthTotal;

// GET /api/v1/reports/expenses (E-RPT-07): business spending, personal drawings and loan interest are three separate
// figures (FR-RPT-08 AC1). Reversed expenses and reversed interest payments are left out; principal repayments and
// chit contributions are not expenses and never appear.
public record ExpenseReport(LocalDate from, LocalDate to, BigDecimal businessTotal, BigDecimal personalDrawingsTotal,
                            BigDecimal interestCost, List<CategoryTotal> businessByCategory,
                            List<MonthTotal> byMonth) {
}

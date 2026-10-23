package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nexora.finance.domain.ExpenseCategory;

// GET /api/v1/expenses/summary (E-FIN-15): recorded expenses of the period; personal drawings never count in the
// business figures (FR-FIN-12). byCategory is business spending only.
public record ExpenseSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal businessTotal,
        BigDecimal personalDrawingsTotal,
        List<CategoryTotal> byCategory,
        List<MonthTotal> byMonth) {

    public record CategoryTotal(String category, BigDecimal total) {
    }

    public record MonthTotal(String month, BigDecimal businessTotal, BigDecimal personalTotal) {
    }
}

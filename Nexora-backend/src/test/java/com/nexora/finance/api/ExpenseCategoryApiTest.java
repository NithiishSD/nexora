package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

class ExpenseCategoryApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-FIN-19 AC1 create category returns 201 and can be used on expenses")
    void createCategory_andRecordExpense() throws Exception {
        String cash = createAccount("Cash", "CASH", "10000.00");

        // 1. Create a custom category "FUEL"
        asOwner(postJson("/api/v1/expense-categories", """
                {"name":"FUEL"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("FUEL"))
                .andExpect(jsonPath("$.active").value(true));

        // 2. Can now record an expense with category "FUEL"
        asOwner(postWithKey("/api/v1/expenses", """
                {"category":"FUEL","expenseKind":"BUSINESS","amount":2500.00,"expenseDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"CASH","description":"Generator diesel"}"""
                .formatted(today(), cash)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("FUEL"))
                .andExpect(jsonPath("$.amount").value(2500.00));

        // 3. Listed in expense summary
        asOwner(get("/api/v1/expenses/summary?from=" + today() + "&to=" + today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byCategory[?(@.category=='FUEL')].total").value(2500.00));
    }

    @Test
    @DisplayName("FR-FIN-19 AC2 duplicate category returns 409 duplicate resource")
    void duplicateCategory_returns409() throws Exception {
        asOwner(postJson("/api/v1/expense-categories", """
                {"name":"STATIONERY"}"""))
                .andExpect(status().isCreated());

        asOwner(postJson("/api/v1/expense-categories", """
                {"name":"STATIONERY"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("FR-FIN-19 invalid category name returns 400 validation failed")
    void invalidCategoryName_returns400() throws Exception {
        // Lowercase or special chars
        asOwner(postJson("/api/v1/expense-categories", """
                {"name":"invalid_name"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Single character
        asOwner(postJson("/api/v1/expense-categories", """
                {"name":"A"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("FR-FIN-19 list active categories includes seeded categories")
    void listCategories_includesSeeded() throws Exception {
        asOwner(get("/api/v1/expense-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='TRANSPORT')]").exists())
                .andExpect(jsonPath("$[?(@.name=='RENT')]").exists())
                .andExpect(jsonPath("$[?(@.name=='ELECTRICITY')]").exists());
    }
}

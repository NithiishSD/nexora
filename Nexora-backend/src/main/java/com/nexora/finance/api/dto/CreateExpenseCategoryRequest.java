package com.nexora.finance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateExpenseCategoryRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z_]{2,30}$", message = "Expense category name must be 2 to 30 uppercase letters or underscores")
        String name) {
}

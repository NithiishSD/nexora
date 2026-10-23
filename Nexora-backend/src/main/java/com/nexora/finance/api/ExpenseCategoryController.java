package com.nexora.finance.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.finance.api.dto.CreateExpenseCategoryRequest;
import com.nexora.finance.api.dto.ExpenseCategoryResponse;
import com.nexora.finance.application.ExpenseCategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/expense-categories")
@Tag(name = "Expense Categories", description = "Configurable expense categories (FR-FIN-19, E-FIN-17)")
public class ExpenseCategoryController {

    private final ExpenseCategoryService service;

    public ExpenseCategoryController(ExpenseCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    @Operation(summary = "List all active expense categories (E-FIN-17)")
    public List<ExpenseCategoryResponse> list() {
        return service.listActive();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new configurable expense category (E-FIN-17)")
    public ExpenseCategoryResponse create(@Valid @RequestBody CreateExpenseCategoryRequest request) {
        return service.create(request.name());
    }
}

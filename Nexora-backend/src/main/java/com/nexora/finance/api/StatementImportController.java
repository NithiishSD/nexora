package com.nexora.finance.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nexora.finance.api.dto.StatementImportResponse;
import com.nexora.finance.application.StatementImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/finance/accounts/{id}/statement-imports")
@Tag(name = "Bank Statement Import", description = "Bank statement reconciliation (FR-FIN-20, E-FIN-18)")
public class StatementImportController {

    private final StatementImportService service;

    public StatementImportController(StatementImportService service) {
        this.service = service;
    }

    // No consumes restriction: permission is checked before request content type is parsed
    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Import a bank statement for reconciliation (E-FIN-18)")
    public StatementImportResponse importStatement(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return service.importStatement(id, file);
    }
}

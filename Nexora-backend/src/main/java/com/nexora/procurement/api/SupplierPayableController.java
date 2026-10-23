package com.nexora.procurement.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.procurement.api.dto.SupplierPayableResponse;
import com.nexora.procurement.api.dto.SupplierPayablesOverviewResponse;
import com.nexora.procurement.application.SupplierPayableQuery;

// Supplier payables (E-PAY-07/08), read only. PAYMENT_VIEW per 05-API-SPEC section 22 #20.
@RestController
@RequestMapping("/api/v1")
class SupplierPayableController {

    private final SupplierPayableQuery payables;

    SupplierPayableController(SupplierPayableQuery payables) {
        this.payables = payables;
    }

    @GetMapping("/suppliers/{id}/payables")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    SupplierPayableResponse forSupplier(@PathVariable UUID id) {
        return payables.forSupplier(id);
    }

    @GetMapping("/payables/suppliers")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    SupplierPayablesOverviewResponse overview(@RequestParam(defaultValue = "false") boolean overdueOnly,
                                              @RequestParam(defaultValue = "oldestDueDate,asc") String sort) {
        return payables.overview(overdueOnly, sort);
    }
}

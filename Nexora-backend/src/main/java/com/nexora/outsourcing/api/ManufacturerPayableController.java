package com.nexora.outsourcing.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.outsourcing.api.dto.ManufacturerPayableResponse;
import com.nexora.outsourcing.application.ManufacturerPayableQuery;

// What is owed to a manufacturer for outsourcing work. Payments themselves go through POST /api/v1/payments.
@RestController
class ManufacturerPayableController {

    private final ManufacturerPayableQuery payables;

    ManufacturerPayableController(ManufacturerPayableQuery payables) {
        this.payables = payables;
    }

    @GetMapping("/api/v1/manufacturers/{id}/payable")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    ManufacturerPayableResponse payable(@PathVariable UUID id) {
        return payables.forManufacturer(id);
    }
}

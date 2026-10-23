package com.nexora.inventory.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyRef;

// GET /api/v1/inventory/external-wip (E-INV-04): material outside with manufacturers, per manufacturer and per open
// job (MATERIAL_ISSUED or PARTIALLY_RECEIVED). outside = issued - received - wastage.
public record ExternalWipResponse(BigDecimal totalOutsideKg, List<Manufacturer> manufacturers) {

    public record Manufacturer(PartyRef manufacturer, BigDecimal issuedKg, BigDecimal receivedKg, BigDecimal wastageKg,
                               BigDecimal outsideKg, List<Job> jobs) {
    }

    public record Job(UUID jobId, String jobNumber, ProductType productType, String status, LocalDate issueDate,
                      BigDecimal issuedKg, BigDecimal receivedKg, BigDecimal wastageKg, BigDecimal outsideKg) {
    }
}

package com.nexora.inventory.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.inventory.api.dto.InventoryThresholdResponse;
import com.nexora.inventory.api.dto.LowStockItemResponse;
import com.nexora.inventory.api.dto.SetThresholdRequest;
import com.nexora.inventory.application.InventoryThresholdService;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/inventory")
class InventoryThresholdController {

    private final InventoryThresholdService thresholdService;

    InventoryThresholdController(InventoryThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    @GetMapping("/thresholds")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    List<InventoryThresholdResponse> getThresholds() {
        return thresholdService.getThresholds();
    }

    @PutMapping("/thresholds/{productType}/{location}")
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    InventoryThresholdResponse setThreshold(
            @PathVariable ProductType productType,
            @PathVariable StockLocation location,
            @Valid @RequestBody SetThresholdRequest request) {
        return thresholdService.setThreshold(productType, location, request.minThresholdKg());
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    List<LowStockItemResponse> getLowStockItems() {
        return thresholdService.getLowStockItems();
    }
}

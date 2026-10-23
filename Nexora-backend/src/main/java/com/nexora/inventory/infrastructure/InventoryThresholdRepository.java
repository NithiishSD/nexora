package com.nexora.inventory.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.inventory.domain.InventoryThreshold;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

public interface InventoryThresholdRepository extends JpaRepository<InventoryThreshold, UUID> {

    Optional<InventoryThreshold> findByProductTypeAndLocation(ProductType productType, StockLocation location);
}

package com.nexora.inventory.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.api.dto.InventoryThresholdResponse;
import com.nexora.inventory.api.dto.LowStockItemResponse;
import com.nexora.inventory.domain.InventoryBalance;
import com.nexora.inventory.domain.InventoryThreshold;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.inventory.infrastructure.InventoryBalanceRepository;
import com.nexora.inventory.infrastructure.InventoryThresholdRepository;
import com.nexora.shared.domain.Weight;

@Service
public class InventoryThresholdService {

    private static final Logger log = LoggerFactory.getLogger(InventoryThresholdService.class);

    private final InventoryThresholdRepository thresholdRepository;
    private final InventoryBalanceRepository balanceRepository;

    public InventoryThresholdService(InventoryThresholdRepository thresholdRepository,
                                    InventoryBalanceRepository balanceRepository) {
        this.thresholdRepository = thresholdRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional
    public InventoryThresholdResponse setThreshold(ProductType productType, StockLocation location, BigDecimal minThresholdKg) {
        InventoryThreshold.validatePair(productType, location);
        InventoryThreshold threshold = thresholdRepository.findByProductTypeAndLocation(productType, location)
                .map(existing -> {
                    existing.update(minThresholdKg);
                    return existing;
                })
                .orElseGet(() -> new InventoryThreshold(productType, location, minThresholdKg));

        InventoryThreshold saved = thresholdRepository.saveAndFlush(threshold);
        log.info("event=inventory_threshold_updated productType={} location={} minThresholdKg={}",
                productType, location, saved.getMinThresholdKg());
        return InventoryThresholdResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<InventoryThresholdResponse> getThresholds() {
        return thresholdRepository.findAll().stream()
                .sorted(Comparator.comparing(InventoryThreshold::getProductType)
                        .thenComparing(InventoryThreshold::getLocation))
                .map(InventoryThresholdResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LowStockItemResponse> getLowStockItems() {
        List<InventoryThreshold> thresholds = thresholdRepository.findAll().stream()
                .filter(t -> t.getMinThresholdKg().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (thresholds.isEmpty()) {
            return List.of();
        }

        Map<ProductLocationKey, BigDecimal> balances = balanceRepository.findAll().stream()
                .collect(Collectors.toMap(
                        b -> new ProductLocationKey(b.getProductType(), b.getLocation()),
                        InventoryBalance::getQuantityKg));

        List<LowStockItemResponse> lowStock = new ArrayList<>();
        for (InventoryThreshold t : thresholds) {
            BigDecimal currentQty = balances.getOrDefault(
                    new ProductLocationKey(t.getProductType(), t.getLocation()),
                    BigDecimal.ZERO);
            if (currentQty.compareTo(t.getMinThresholdKg()) <= 0) {
                BigDecimal deficit = Weight.round(t.getMinThresholdKg().subtract(currentQty));
                lowStock.add(new LowStockItemResponse(
                        t.getProductType(),
                        t.getLocation(),
                        Weight.round(currentQty),
                        t.getMinThresholdKg(),
                        deficit));
            }
        }

        lowStock.sort(Comparator.comparing(LowStockItemResponse::productType)
                .thenComparing(LowStockItemResponse::location));
        return lowStock;
    }

    private record ProductLocationKey(ProductType productType, StockLocation location) {
    }
}

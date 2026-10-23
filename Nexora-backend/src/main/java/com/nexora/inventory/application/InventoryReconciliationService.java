package com.nexora.inventory.application;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.inventory.infrastructure.InventoryBalanceRepository;

// FR-INV-09: the stored balances are only a cache of the ledger. This check recomputes every balance from the
// movements and lists each (product, location) where the two disagree. An empty list = consistent.
@Service
public class InventoryReconciliationService {

    private final InventoryBalanceRepository balances;

    public InventoryReconciliationService(InventoryBalanceRepository balances) {
        this.balances = balances;
    }

    @Transactional(readOnly = true)
    public List<Difference> findDifferences() {
        return balances.findDifferencesFromLedger().stream()
                .map(d -> new Difference(ProductType.valueOf(d.getProductType()), StockLocation.valueOf(d.getLocation()),
                        d.getExpectedKg(), d.getActualKg()))
                .toList();
    }

    // expectedKg = in minus out from the ledger; actualKg = the stored balance (0 when the row is missing).
    public record Difference(ProductType productType, StockLocation location, BigDecimal expectedKg,
                             BigDecimal actualKg) {
    }
}

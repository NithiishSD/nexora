package com.nexora.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.DatabaseCleaner;

class InventoryReconciliationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    InventoryService inventory;

    @Autowired
    InventoryReconciliationService reconciliation;

    @Autowired
    InventoryReconciliationJob job;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("FR-INV-09 NFR-REL-01 balances equal sum of movements")
    void balances_equalSumOfMovements() {
        // a mix of every kind of movement M2 can produce, including a conversion and a two-sided adjustment
        record(MovementType.OPENING_BALANCE, ProductType.RAW_SILK, null, null, StockLocation.RAW_STOCK, "100.000");
        record(MovementType.RECEIPT, ProductType.RAW_SILK, null, StockLocation.SUPPLIER, StockLocation.RAW_STOCK, "95.000");
        record(MovementType.RECEIPT, ProductType.VUDA_WARP, null, StockLocation.SUPPLIER, StockLocation.RAW_STOCK, "50.000");
        record(MovementType.PURCHASE_RETURN, ProductType.RAW_SILK, null, StockLocation.RAW_STOCK, StockLocation.RETURNED, "5.000");
        record(MovementType.CONSUMPTION, ProductType.RAW_SILK, ProductType.KORA_WARP, StockLocation.RAW_STOCK,
                StockLocation.INTERNAL_WIP, "40.000");
        record(MovementType.PRODUCTION_OUTPUT, ProductType.KORA_WARP, null, StockLocation.INTERNAL_WIP,
                StockLocation.FINISHED_STOCK, "37.500");
        record(MovementType.WASTAGE, ProductType.KORA_WARP, null, StockLocation.INTERNAL_WIP, StockLocation.WASTAGE, "2.500");
        record(MovementType.ADJUSTMENT, ProductType.KORA_WARP, null, StockLocation.FINISHED_STOCK,
                StockLocation.INTERNAL_WIP, "1.000");

        // AC1: consistent data -> no difference (and the nightly job runs cleanly)
        assertThat(reconciliation.findDifferences()).isEmpty();
        job.run();

        // AC2: a balance edited behind the ledger's back is reported with expected and actual quantity
        jdbc.update("update inventory_balances set quantity_kg = 999 where product_type = 'KORA_WARP'"
                + " and location = 'FINISHED_STOCK'");
        List<InventoryReconciliationService.Difference> differences = reconciliation.findDifferences();
        assertThat(differences).hasSize(1);
        InventoryReconciliationService.Difference d = differences.get(0);
        assertThat(d.productType()).isEqualTo(ProductType.KORA_WARP);
        assertThat(d.location()).isEqualTo(StockLocation.FINISHED_STOCK);
        assertThat(d.expectedKg()).isEqualByComparingTo("36.500");
        assertThat(d.actualKg()).isEqualByComparingTo("999");
        job.run();                                         // logs the mismatch at ERROR, changes nothing
        assertThat(reconciliation.findDifferences()).hasSize(1);
    }

    private void record(MovementType type, ProductType product, ProductType toProduct, StockLocation from,
                        StockLocation to, String kg) {
        boolean referenced = type != MovementType.OPENING_BALANCE && type != MovementType.ADJUSTMENT
                && type != MovementType.WASTAGE;
        new TransactionTemplate(transactionManager).execute(s -> inventory.recordMovement(new MovementCommand(
                product, toProduct, type, from, to, new BigDecimal(kg), LocalDate.of(2026, 10, 1),
                referenced ? MovementReferenceType.PURCHASE : null, referenced ? UUID.randomUUID() : null,
                referenced ? "REF-TEST-0001" : null, type == MovementType.ADJUSTMENT ? "recount" : null, null)));
    }
}

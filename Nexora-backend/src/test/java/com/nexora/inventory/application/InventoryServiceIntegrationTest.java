package com.nexora.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.DatabaseCleaner;

class InventoryServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    InventoryService inventory;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @BeforeEach
    void clean() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("FR-INV-01 FR-INV-02 FR-INV-04 record movement writes movement and balance in one transaction")
    void recordMovement_writesMovementAndBalanceInOneTransaction() {
        UUID receiptId = UUID.randomUUID();

        MovementResult result = tx().execute(s -> inventory.recordMovement(receipt("95.000", receiptId)));

        // one ledger row with all the facts of FR-INV-02, and the balance moved by exactly that much
        assertThat(result.movementNumber()).isEqualTo("MOV-" + Year.now(clock).getValue() + "-0001");
        Map<String, Object> row = jdbc.queryForMap("select * from material_movements where id = ?", result.id());
        assertThat(row).containsEntry("movement_type", "RECEIPT").containsEntry("from_location", "SUPPLIER")
                .containsEntry("to_location", "RAW_STOCK").containsEntry("reference_type", "MATERIAL_RECEIPT")
                .containsEntry("reference_id", receiptId).containsEntry("reference_number", "RCT-TEST-0001");
        assertThat((BigDecimal) row.get("quantity_kg")).isEqualByComparingTo("95.000");
        assertThat(balance(ProductType.RAW_SILK, StockLocation.RAW_STOCK)).isEqualByComparingTo("95.000");

        // FR-INV-04 AC2: when the surrounding use case rolls back, neither the movement nor the balance change stays
        tx().executeWithoutResult(s -> {
            inventory.recordMovement(receipt("5.000", UUID.randomUUID()));
            s.setRollbackOnly();
        });
        assertThat(movementCount()).isEqualTo(1);
        assertThat(balance(ProductType.RAW_SILK, StockLocation.RAW_STOCK)).isEqualByComparingTo("95.000");
    }

    @Test
    @DisplayName("FR-INV-05 NFR-REL-01 record movement more than available throws insufficient inventory")
    void recordMovement_moreThanAvailable_throwsInsufficientInventory() {
        tx().execute(s -> inventory.recordMovement(opening(ProductType.RAW_SILK, StockLocation.RAW_STOCK, "100.000")));

        // FR-INV-05 AC2: 120 kg out of 100 kg is refused, the message names what is available
        assertThatThrownBy(() -> tx().execute(s -> inventory.recordMovement(wastage("120.000"))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY);
                    assertThat(e.getMessage()).contains("100.000").contains("RAW_SILK").contains("RAW_STOCK");
                });
        assertThat(balance(ProductType.RAW_SILK, StockLocation.RAW_STOCK)).isEqualByComparingTo("100.000");
        assertThat(movementCount()).isEqualTo(1);

        // AC1: 80 kg out of 100 kg leaves 20 kg
        tx().execute(s -> inventory.recordMovement(wastage("80.000")));
        assertThat(balance(ProductType.RAW_SILK, StockLocation.RAW_STOCK)).isEqualByComparingTo("20.000");
    }

    @Test
    @DisplayName("NFR-REL-02 record movement outside transaction is rejected")
    void recordMovement_outsideTransaction_isRejected() {
        assertThatThrownBy(() -> inventory.recordMovement(receipt("1.000", UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(movementCount()).isZero();
    }

    @Test
    @DisplayName("FR-INV-03 record movement conversion to warp updates wip under target product")
    void recordMovement_conversionToWarp_updatesWipUnderTargetProduct() {
        tx().execute(s -> inventory.recordMovement(opening(ProductType.RAW_SILK, StockLocation.RAW_STOCK, "100.000")));

        // raw silk leaves raw stock and arrives in internal WIP as the kora warp being made
        tx().execute(s -> inventory.recordMovement(new MovementCommand(ProductType.RAW_SILK, ProductType.KORA_WARP,
                MovementType.CONSUMPTION, StockLocation.RAW_STOCK, StockLocation.INTERNAL_WIP,
                new BigDecimal("40.000"), LocalDate.now(clock), MovementReferenceType.PRODUCTION_BATCH,
                UUID.randomUUID(), "BAT-TEST-0001", null, null)));

        assertThat(balance(ProductType.RAW_SILK, StockLocation.RAW_STOCK)).isEqualByComparingTo("60.000");
        assertThat(balance(ProductType.KORA_WARP, StockLocation.INTERNAL_WIP)).isEqualByComparingTo("40.000");
        // no raw silk row in WIP: WIP is tracked as warp (the CHECK would reject one anyway)
        assertThat(jdbc.queryForObject("select count(*) from inventory_balances where location = 'INTERNAL_WIP'"
                + " and product_type = 'RAW_SILK'", Integer.class)).isZero();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private MovementCommand receipt(String kg, UUID receiptId) {
        return new MovementCommand(ProductType.RAW_SILK, null, MovementType.RECEIPT, StockLocation.SUPPLIER,
                StockLocation.RAW_STOCK, new BigDecimal(kg), LocalDate.now(clock), MovementReferenceType.MATERIAL_RECEIPT,
                receiptId, "RCT-TEST-0001", null, null);
    }

    private MovementCommand wastage(String kg) {
        return new MovementCommand(ProductType.RAW_SILK, null, MovementType.WASTAGE, StockLocation.RAW_STOCK,
                StockLocation.WASTAGE, new BigDecimal(kg), LocalDate.now(clock), null, null, null, "moisture loss", null);
    }

    private MovementCommand opening(ProductType product, StockLocation location, String kg) {
        return new MovementCommand(product, null, MovementType.OPENING_BALANCE, null, location, new BigDecimal(kg),
                LocalDate.now(clock), null, null, null, null, null);
    }

    private BigDecimal balance(ProductType product, StockLocation location) {
        return jdbc.queryForObject("select quantity_kg from inventory_balances where product_type = ? and location = ?",
                BigDecimal.class, product.name(), location.name());
    }

    private int movementCount() {
        return jdbc.queryForObject("select count(*) from material_movements", Integer.class);
    }
}

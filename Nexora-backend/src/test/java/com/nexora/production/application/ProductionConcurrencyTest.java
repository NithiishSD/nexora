package com.nexora.production.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
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

import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyService;
import com.nexora.production.api.dto.BatchResponse;
import com.nexora.production.api.dto.CreateBatchRequest;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;
import com.nexora.support.DatabaseCleaner;
import com.nexora.support.TestDataFactory;

// FR-PROD-09: two batches started at the same moment compete for one raw-silk balance. Mutation check (by hand, never
// committed): without @Lock(PESSIMISTIC_WRITE) on InventoryBalanceRepository.findForUpdate the loser fails with an
// optimistic-lock error instead of INSUFFICIENT_INVENTORY.
class ProductionConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    ProductionService production;

    @Autowired
    InventoryService inventory;

    @Autowired
    PartyService partyService;

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
    @DisplayName("FR-PROD-09 NFR-TEST-05 parallel starts cannot oversubscribe raw silk")
    void parallelStarts_cannotOversubscribeRawSilk() throws Exception {
        new TestDataFactory(partyService, inventory, new TransactionTemplate(transactionManager)).rawSilkInStock("110.000");
        // an earlier batch creates the INTERNAL_WIP balance row; otherwise the second start waits on that row's insert
        // (unique index) and the race is hidden even without the balance lock
        production.start(plan("10.000"), null);
        UUID first = plan("80.000");
        UUID second = plan("70.000");

        List<Outcome<BatchResponse>> outcomes = ConcurrentRunner.runTogether(List.of(
                () -> production.start(first, null), () -> production.start(second, null)));

        // exactly one starts; the other sees the reduced stock and stays PLANNED
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow().error())
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY));
        BigDecimal left = jdbc.queryForObject("select quantity_kg from inventory_balances where product_type ="
                + " 'RAW_SILK' and location = 'RAW_STOCK'", BigDecimal.class);
        assertThat(left.compareTo(new BigDecimal("20.000")) == 0 || left.compareTo(new BigDecimal("30.000")) == 0)
                .as("raw silk is 20 or 30, never negative, got %s", left).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from production_batches where status = 'PLANNED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from material_movements where movement_type = 'CONSUMPTION'",
                Integer.class)).isEqualTo(2);
    }

    private UUID plan(String kg) {
        return production.create(new CreateBatchRequest(ProductType.KORA_WARP, LocalDate.now(clock), new BigDecimal(kg),
                null, null)).id();
    }
}

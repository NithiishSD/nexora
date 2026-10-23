package com.nexora.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;
import com.nexora.support.DatabaseCleaner;

// Proves the balance lock (08-TESTING section 9). Each task runs in its own transaction on its own thread,
// all released at the same moment. Mutation check (done by hand at the M2 gate, never committed): without
// @Lock(PESSIMISTIC_WRITE) on InventoryBalanceRepository.findForUpdate the first test fails; locking rows in
// request order instead of (product, location) order makes the second one fail with a deadlock, and locking
// each movement of a batch separately makes the multi-movement test fail with a deadlock.
class InventoryConcurrencyTest extends AbstractIntegrationTest {

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
    @DisplayName("FR-INV-05 NFR-TEST-05 parallel80 and70 from100 exactly one succeeds")
    void parallel80And70From100_exactlyOneSucceeds() throws Exception {
        record(opening(ProductType.RAW_SILK, StockLocation.RAW_STOCK));

        List<Outcome<MovementResult>> outcomes = ConcurrentRunner.runTogether(List.of(
                () -> record(wastage("80.000")),
                () -> record(wastage("70.000"))));

        // exactly one wins; the loser sees the reduced stock and is refused with the business error
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        Throwable loser = outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow().error();
        assertThat(loser).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY));
        BigDecimal left = balance(ProductType.RAW_SILK, StockLocation.RAW_STOCK);
        assertThat(left.compareTo(new BigDecimal("20.000")) == 0 || left.compareTo(new BigDecimal("30.000")) == 0)
                .as("balance is 20 or 30, never negative, got %s", left).isTrue();
        // the projection still equals the ledger: opening 100 minus the one stored wastage
        assertThat(jdbc.queryForObject("select 100 - sum(quantity_kg) from material_movements"
                + " where movement_type = 'WASTAGE'", BigDecimal.class)).isEqualByComparingTo(left);
    }

    @Test
    @DisplayName("FR-INV-06 opposite two balance movements do not deadlock")
    void oppositeTwoBalanceMovements_doNotDeadlock() throws Exception {
        record(opening(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK));
        record(opening(ProductType.SILK_WARP, StockLocation.INTERNAL_WIP));

        // 10 rounds of 10 parallel movements, half finished -> WIP and half WIP -> finished: 100 movements that
        // each lock BOTH rows. In request order they would lock A then B and B then A: a deadlock.
        for (int round = 0; round < 10; round++) {
            List<Callable<MovementResult>> tasks = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                tasks.add(() -> record(adjust(StockLocation.FINISHED_STOCK, StockLocation.INTERNAL_WIP)));
                tasks.add(() -> record(adjust(StockLocation.INTERNAL_WIP, StockLocation.FINISHED_STOCK)));
            }
            List<Outcome<MovementResult>> outcomes = ConcurrentRunner.runTogether(tasks);
            assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).as("no deadlock or timeout").isNull());
        }

        // every movement went through and the two directions cancel out
        assertThat(jdbc.queryForObject("select count(*) from material_movements where movement_type = 'ADJUSTMENT'",
                Integer.class)).isEqualTo(100);
        assertThat(balance(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("100.000");
        assertThat(balance(ProductType.SILK_WARP, StockLocation.INTERNAL_WIP)).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("FR-INV-06 FR-ORD-12 opposite multi movement batches do not deadlock")
    void oppositeMultiMovementBatches_doNotDeadlock() throws Exception {
        record(opening(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK));
        record(opening(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK));

        // Two-item deliveries, half silk-then-kora and half kora-then-silk. Locking item by item (and taking a MOV
        // number in between) would deadlock; recordMovements locks both rows first, in the global order.
        for (int round = 0; round < 10; round++) {
            List<Callable<List<MovementResult>>> tasks = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                tasks.add(() -> recordAll(delivery(ProductType.SILK_WARP), delivery(ProductType.KORA_WARP)));
                tasks.add(() -> recordAll(delivery(ProductType.KORA_WARP), delivery(ProductType.SILK_WARP)));
            }
            List<Outcome<List<MovementResult>>> outcomes = ConcurrentRunner.runTogether(tasks);
            assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).as("no deadlock or timeout").isNull());
        }

        assertThat(jdbc.queryForObject("select count(*) from material_movements where movement_type = 'DELIVERY'",
                Integer.class)).isEqualTo(200);
        assertThat(balance(ProductType.SILK_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("0.000");
        assertThat(balance(ProductType.KORA_WARP, StockLocation.FINISHED_STOCK)).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("FR-INV-06 balance locked longer than five seconds fails with concurrent modification")
    void balanceLockedLongerThanFiveSeconds_failsWithConcurrentModification() throws Exception {
        record(opening(ProductType.RAW_SILK, StockLocation.RAW_STOCK));
        // every connection carries the 5 s lock timeout (ADR-030)
        assertThat(jdbc.queryForObject("show lock_timeout", String.class)).isEqualTo("5s");

        // another transaction holds the raw-silk row lock and keeps it until we let it go
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            Future<?> holding = holder.submit(() -> tx().executeWithoutResult(s -> {
                jdbc.queryForList("select id from inventory_balances where product_type = 'RAW_SILK'"
                        + " and location = 'RAW_STOCK' for update");
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            long start = System.nanoTime();
            // mapped to 409 CONCURRENT_MODIFICATION by GlobalExceptionHandler.handlePessimisticLock
            assertThatThrownBy(() -> record(wastage("1.000"))).isInstanceOf(PessimisticLockingFailureException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(15));

            release.countDown();
            holding.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            holder.shutdownNow();
        }
        // nothing was stored by the timed-out request
        assertThat(jdbc.queryForObject("select count(*) from material_movements", Integer.class)).isEqualTo(1);
        assertThat(balance(ProductType.RAW_SILK, StockLocation.RAW_STOCK)).isEqualByComparingTo("100.000");
    }

    private MovementResult record(MovementCommand command) {
        return tx().execute(s -> inventory.recordMovement(command));
    }

    private List<MovementResult> recordAll(MovementCommand... commands) {
        return tx().execute(s -> inventory.recordMovements(List.of(commands)));
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private MovementCommand opening(ProductType product, StockLocation location) {
        return new MovementCommand(product, null, MovementType.OPENING_BALANCE, null, location,
                new BigDecimal("100.000"), LocalDate.now(clock), null, null, null, null, null);
    }

    private MovementCommand wastage(String kg) {
        return new MovementCommand(ProductType.RAW_SILK, null, MovementType.WASTAGE, StockLocation.RAW_STOCK,
                StockLocation.WASTAGE, new BigDecimal(kg), LocalDate.now(clock), null, null, null, "spillage", null);
    }

    private MovementCommand delivery(ProductType product) {
        return new MovementCommand(product, null, MovementType.DELIVERY, StockLocation.FINISHED_STOCK,
                StockLocation.CUSTOMER, new BigDecimal("1.000"), LocalDate.now(clock), MovementReferenceType.ORDER_ITEM,
                UUID.randomUUID(), "ORD-2026-0001", null, null);
    }

    private MovementCommand adjust(StockLocation from, StockLocation to) {
        return new MovementCommand(ProductType.SILK_WARP, null, MovementType.ADJUSTMENT, from, to,
                new BigDecimal("1.000"), LocalDate.now(clock), null, null, null, "recount", null);
    }

    private BigDecimal balance(ProductType product, StockLocation location) {
        return jdbc.queryForObject("select quantity_kg from inventory_balances where product_type = ? and location = ?",
                BigDecimal.class, product.name(), location.name());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
